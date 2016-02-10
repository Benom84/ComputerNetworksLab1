import java.io.* ;
import java.net.* ;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Pattern;

import javax.net.ssl.HostnameVerifier;

final class HttpRequest implements Runnable
{
	final static String CRLF = "\r\n";
	final static String NEWLINE = System.lineSeparator();
	final static int CHUNCKED_BYTES = 1;
	private File rootDirectory;
	private File defaultPage;
	private SynchronizedQueue<Socket> socketRequestsQueue;
	public String fullPathForFile;
	private Crawler serverCrawler;


	// Constructor
	public HttpRequest(File rootDirectory, File defaultPage, SynchronizedQueue<Socket> socketRequestsQueue, int threadNumber, Crawler crawler)
	{
		this.rootDirectory = rootDirectory;
		this.defaultPage = defaultPage;
		this.socketRequestsQueue = socketRequestsQueue;
		this.serverCrawler = crawler;
	}

	// Implement the run() method of the Runnable interface.
	public void run()
	{
		try
		{
			processRequest();
		}
		catch (Exception e)
		{
			System.out.println("Request generating error: " + e);
		}
	}

	private void processRequest() throws Exception
	{
		while (true) {

			Socket socket = socketRequestsQueue.take();
			
			DataOutputStream socketOutputStream = new DataOutputStream(socket.getOutputStream());

			HtmlRequest htmlRequest = readRequest(socket);
			
			// If the request is empty than the socket was closed on the other side
			if (htmlRequest.equals(null)) {

				try {
					socket.close();
				} catch (Exception e) {
					System.out.println("Error on trying to close socket on empty request: " + e.toString());
				}
				continue;
			}
			
			HtmlResponse responseToClient;

			if (!htmlRequest.isLegalRequest) {
				// The request format is illegal
				responseToClient = respond400(htmlRequest);
			} else if (!legalRequestType(htmlRequest)) {
				// The request method is unimplemented
				responseToClient = respond501(htmlRequest);
			} else if (directRequestToResultPages(htmlRequest)) {
				responseToClient = respond403(htmlRequest);
				System.out.println("Failed on referrer");
				//responesClient = respond403(htmlRequest);
			} else {
				if (!htmlRequest.type.equals("TRACE") && !htmlRequest.type.equals("POST")) {
					boolean isFileLegal = false;
					try {
						isFileLegal = checkIfRequestedFileLegal(htmlRequest);
					} catch (IOException e) {
						System.out.println("Error checking the file: " + htmlRequest.requestedFile);
						responseToClient = respond500(htmlRequest);
					}
					if (!isFileLegal) {
						responseToClient = respond404(htmlRequest);
					} else {
						responseToClient = respond200(htmlRequest);
					}	
				} else {
					responseToClient = respond200(htmlRequest);
				}
				
			}

			if(htmlRequest.isChunked){
				System.out.println(responseToClient.getStatusLine() + responseToClient.getContentType() + 
					responseToClient.getContentLengthLine() + responseToClient.getTransferEncoding());
			}else{
				System.out.println(responseToClient.getStatusLine() + responseToClient.getContentType() + 
					responseToClient.getContentLengthLine());
			}
			try {
				System.out.println("The HTTP response header returning to the browser.");
				// Send the status line.
				socketOutputStream.writeBytes(responseToClient.getStatusLine());

				// Send the content type line.
				socketOutputStream.writeBytes(responseToClient.getContentType());

				// Send content length.
				if (!htmlRequest.isChunked) {
					socketOutputStream.writeBytes(responseToClient.getContentLengthLine());	
				}
				
				if(htmlRequest.isChunked){
					socketOutputStream.writeBytes(responseToClient.getTransferEncoding());
				}
				// Send a blank line to indicate the end of the header lines.
				socketOutputStream.writeBytes(CRLF);

			} catch (Exception e) {
				System.out.println("Writing the header caused an error" + e.toString());
			}

			// Send the content of the HTTP.
			if (!htmlRequest.type.equals("HEAD")) {
				sendEntityBodyToClient(socketOutputStream, responseToClient, htmlRequest.isChunked);
			}			


			// Close streams and socket.
			try {
				socketOutputStream.close();
				socket.close();	
			} catch (Exception e) {
				System.out.println("closing the socket caused an error");
			}
		}
	}

	private boolean directRequestToResultPages(HtmlRequest htmlRequest) {
		String filename = htmlRequest.requestedFile;
		
		if (filename.length() < Crawler.RESULTS_PATH_WEB.length()) {
			return false;
		}
		
		if (filename.substring(0, Crawler.RESULTS_PATH_WEB.length()).equalsIgnoreCase(Crawler.RESULTS_PATH_WEB)) {
			System.out.println("********************************************************");
			String referrer = null;
			for (String line : htmlRequest.parsedRequest) {
				if (line.startsWith("Referer:"))  {
					System.out.println(line + " EOL!");
					//Referer: http://xxx.xxx.xxx.xxx/
					if (line.length() < 16) {
						return false;
					}
					
					String[] splitReferer = line.substring(16, line.length()).split("/");
					for (String string : splitReferer) {
						System.out.println("splitReferer: " + string);
					}
					
					System.out.println("Comparing to: " + defaultPage.getName());
					if (splitReferer.length < 2 || splitReferer[1].equalsIgnoreCase(defaultPage.getName())) {
						return false;
					}
				}
					
			}
			System.out.println("********************************************************");
			
			//System.out.println("Referrer is: " + referrer);
			//TODO
			if (referrer == null) {
				return true;
			}
		}
		
		return false;
	}

	private boolean legalRequestType(HtmlRequest htmlRequest) {

		if (htmlRequest.type == null) {
			return false;
		}

		if (!htmlRequest.type.equals("POST") && !htmlRequest.type.equals("GET") && 
				!htmlRequest.type.equals("HEAD") && !htmlRequest.type.equals("TRACE")) {
			return false;
		}

		return true;
	}

	private HtmlResponse respond200(HtmlRequest htmlRequest) throws IOException, InterruptedException {
		HtmlResponse response200 = new HtmlResponse();
		byte[] bodyInBytes;
		
		if (htmlRequest.type.equals("TRACE")) {
			bodyInBytes = htmlRequest.unparsedRequest.getBytes();
		}else if(htmlRequest.type.equals("POST")){
			if (htmlRequest.requestedFile.equals("/params_info.html")) {
				bodyInBytes = makeTable(htmlRequest.parametersInRequestBody);
			}else if(htmlRequest.requestedFile.equals("/execResult.html")){
				System.out.println("***Parameters for Crawler : " + htmlRequest.parametersInRequestBody.toString());
				if(serverCrawler.isBusy()){
					bodyInBytes = readFileForResponse("/Crawler/CrawlerStillRunning.html");
				}else{

					String crawlerInputCheckResults = checkCrawlerInput(htmlRequest);
					if (crawlerInputCheckResults == null) {
						bodyInBytes = activateCrawler(htmlRequest);
						Thread crawlerThread = new Thread(serverCrawler);
						crawlerThread.start();	
					} else {
						bodyInBytes = prepareDefaultPage(crawlerInputCheckResults);
					}
					
				}
			}
			else{
				bodyInBytes = null;
			}
		}
		else {
			bodyInBytes = readFileForResponse(htmlRequest);
		}

		response200.setEntityBody(bodyInBytes);
		response200.setStatus(htmlRequest.httpVersion, 200);
		String contentType;
		
		if (!htmlRequest.type.equals("POST")) {
			contentType = getContentTypeFromFile(htmlRequest.requestedFile);
			System.out.println("Requested file is: " + htmlRequest.requestedFile + " and type is: " +contentType);
		} else {
			//contentType = getContentTypeFromFile("");
			contentType = getContentTypeFromFile(htmlRequest.requestedFile);
			System.out.println("Requested file is: " + htmlRequest.requestedFile + " and type is: " +contentType);
		}
			
		response200.setContentTypeLine(contentType);

		return response200;
	}
	

	private String checkCrawlerInput(HtmlRequest htmlRequest) {
		
		String result = null;
		String domain = htmlRequest.parametersInRequestBody.get("Domain");
		String domainFound = Crawler.ParseURL(domain);

		if (domainFound.charAt(domainFound.length() - 1) == '\\') {
			domainFound= domainFound.substring(0, domainFound.length() - 1);
		}
		
		try {
			ClientRequest clientRequest = new ClientRequest(domainFound, ClientRequest.getRequest);
			if (clientRequest.responseHeaderFields == null) {
				return "Error connecting to: " + domain;
			}
			System.out.println("checkCrawlerInput: ClientRequest returned: ");
			
			for (String key : clientRequest.responseHeaderFields.keySet()) {
				System.out.println(key + ":\t\t" + clientRequest.responseHeaderFields.get(key));
			}
		} catch (Exception e) {
			System.out.println("checkCrawlerInput: clientRequest generated error.");
			result = "Error connecting to: " + domain;
			e.printStackTrace();
		}

		return result;
	}

	private byte[] activateCrawler(HtmlRequest htmlRequest) throws IOException {
		
		byte[] bodyInBytes;
		String domain = htmlRequest.parametersInRequestBody.get("Domain");
		boolean ignoreRobots = false;
		boolean performPortScan = false;

		if(htmlRequest.parametersInRequestBody.containsKey("portscan")){
			performPortScan = true;
		}
		if(htmlRequest.parametersInRequestBody.containsKey("robots.txt")){
			ignoreRobots = true;
		}
		System.out.println("HttpRequest is configuring Crawler.");
		boolean isConfigureSucceeded = serverCrawler.ConfigureCrawler(domain, ignoreRobots, performPortScan);
		System.out.println("HttpRequest is configuring Crawler. Results: " + isConfigureSucceeded);
		if (isConfigureSucceeded) {
			bodyInBytes = prepareDefaultPage("Crawler started succesfuly");
			System.out.println("Domain is: " + domain);
			System.out.println("Perform port scan: " + performPortScan);
			System.out.println("Ignore robots.txt: " + ignoreRobots);
			//Thread serverCrawlerThread = new Thread(serverCrawler);
			//serverCrawlerThread.start();
		} else {
			bodyInBytes = prepareDefaultPage("Crawler is already running");
		}
		
		return bodyInBytes;

	}

	private byte[] readFileForResponse(HtmlRequest htmlRequest) throws IOException {

		System.out.println("Requested File is: " + htmlRequest.requestedFile + " and default page is " + defaultPage.getName());
		if(htmlRequest.requestedFile.equals("/") || htmlRequest.requestedFile.equals("/" + defaultPage.getName())){
			fullPathForFile = rootDirectory.getCanonicalPath() + "\\" + defaultPage.getName();
			System.out.println("preparing default page");
			return prepareDefaultPage(null);
		}else{
			fullPathForFile = rootDirectory.getCanonicalPath() + htmlRequest.requestedFile;
		}

		File file = new File (fullPathForFile);
		byte [] buffer  = new byte [(int)file.length()];
		FileInputStream fis = new FileInputStream(file);
		BufferedInputStream bis = new BufferedInputStream(fis);
		bis.read(buffer,0,buffer.length);
		bis.close();
		
		return buffer;
	}

	private byte[] prepareDefaultPage(String message) {
		String head = "<!doctype html><html lang=\"en\"><head><title> Crawler HTML site </title></head>"
				+ "<link href=\"css/style.css\" rel=\"stylesheet\" /><body><div class=\"header\"><h1>Crawler</h1></div>";
		String form;
		if (message != null) {
			form = "<div class=\"crawlerAnswer\"><h2>" + message + "</h2>"
					+ "<a href=\"/\"><h3>Back to homepage</h3></a></div>";
		
			
		}
		else if (serverCrawler.isBusy()) {
			form = "<div class=\"crawlerAnswer\"><h2>Crawler is already running</h2></div>";
		} else {
			form = "<div class=\"crawlerForm\"><form id=\"generalform\" method=\"post\" action=\"execResult.html\" class=\"crawlerFormTable\">"
					+ "<table><tr><td><h3>Enter Domain</h3></td><td><input type=\"text\" name=\"Domain\"></td></tr><tr>"
					+ "<td><h3><input type=\"checkbox\" name=\"portscan\">Perform full TCP port scan</h3></td></tr><tr>"
					+ "<td><h3><input type=\"checkbox\" name=\"robots.txt\">Disrespect robots.txt</h3></td></tr>"
					+ "<tr><td></td><td><input type=\"submit\" value=\"Start Crawler\"></td></tr></table></form></div>";
		}
		
		String resultPages = prepareResultPagesSection();
		
		String finish = "</body></html>";
		String result = head + form + resultPages + finish; 
		return result.getBytes();
	}

	private String prepareResultPagesSection() {
		
		String resultsPath;
		try {
			resultsPath = rootDirectory.getCanonicalPath() + Crawler.RESULTS_PATH_LOCAL;
		} catch (IOException e) {
			System.out.println("HTTPRequest: Error root directory" + rootDirectory.toString());
			return "";
		}
		
		System.out.println("PrepareResultPagesSection: path is: " + resultsPath);
		StringBuilder result = new StringBuilder(); 
		result.append("<div class=\"connectedDomains\"><ul>");
		File resultsFolder = new File(resultsPath);
		if (resultsFolder.exists() && resultsFolder.isDirectory()) {
			File[] allFiles = resultsFolder.listFiles();
			SimpleDateFormat format = new SimpleDateFormat("dd.MM.yyyy-HH:mm");
			for (File file : allFiles) {
				String filename = file.getName();
				String domain = Crawler.ResultsFilenameToDomain(filename);
				Date creationDate = Crawler.ResultsFilenameToDate(filename);
				String linkFormat = domain + "-" + format.format(creationDate);
				result.append("<li><a href=");
				result.append(Crawler.RESULTS_PATH_WEB);
				result.append(filename);
				result.append(">");
				result.append(linkFormat);
				result.append("</a></li>");
			}
			
			result.append("</ul></div>");
		}
		
		return result.toString();
	}

	private byte[] readFileForResponse(String filepath) throws IOException {

		if(filepath.equals("/")){
			fullPathForFile = rootDirectory.getCanonicalPath() + "\\" + defaultPage.getName();
		}else{
			fullPathForFile = rootDirectory.getCanonicalPath() + "\\" + filepath;
		}

		File file = new File (fullPathForFile);
		byte [] buffer  = new byte [(int)file.length()];
		FileInputStream fis = new FileInputStream(file);
		BufferedInputStream bis = new BufferedInputStream(fis);
		bis.read(buffer,0,buffer.length);
		bis.close();

		return buffer;
	}

	private String getContentTypeFromFile(String requestedFile) {

		String type = "application/octet-stream";

		// We will check if the file has a type like .bmp .jpg
		String[] splitFileRequest = requestedFile.split(Pattern.quote("."));

		if (splitFileRequest.length < 2)
			return type;

		// If there is a type it is last
		String typeInRequest = splitFileRequest[splitFileRequest.length - 1];
		if (typeInRequest.equalsIgnoreCase("html") || typeInRequest.equalsIgnoreCase("htm"))
			type = "text/html";
		else if (typeInRequest.equalsIgnoreCase("bmp") || typeInRequest.equalsIgnoreCase("png") || typeInRequest.equalsIgnoreCase("jpg")
				|| typeInRequest.equalsIgnoreCase("jpeg") || typeInRequest.equalsIgnoreCase("gif"))
			type = "image";
		else if (typeInRequest.equalsIgnoreCase("ico"))
			type = "icon";
		else if (typeInRequest.equalsIgnoreCase("css"))
			type = "text/css";

		return type;
	}

	private HtmlResponse respond404(HtmlRequest htmlRequest) {
		HtmlResponse response404 = new HtmlResponse();

		response404.setStatus(htmlRequest.httpVersion, 404);
		response404.setContentTypeLine("text/html");
		String body404 = "<HTML><HEAD><TITLE>404 Not Found</TITLE></HEAD>" +
				"<BODY><H1>File Not Found</H1><H2>But have a nice day</H2></BODY></HTML>";
		response404.setEntityBody(body404.getBytes());

		return response404;
	}

	private HtmlResponse respond501(HtmlRequest htmlRequest) {
		HtmlResponse response501 = new HtmlResponse();

		response501.setStatus(htmlRequest.httpVersion, 501);
		response501.setContentTypeLine("text/html");
		String body501 = "<HTML><HEAD><TITLE>Unknown Method</TITLE></HEAD>" +
				"<BODY><H1>Unknown Method</H1><H2>But may you find knowledge in your path</H2></BODY></HTML>";
		response501.setEntityBody(body501.getBytes());

		return response501;
	}

	private HtmlResponse respond500(HtmlRequest htmlRequest) {
		HtmlResponse response500 = new HtmlResponse();

		response500.setStatus(htmlRequest.httpVersion, 500);
		response500.setContentTypeLine("text/html");
		String body500 = "<HTML><HEAD><TITLE>Internal Server Error</TITLE></HEAD>" +
				"<BODY><H1>Internal Server Error</H1><H2>This is totally our fault, so relax.</H2></BODY></HTML>";
		response500.setEntityBody(body500.getBytes());

		return response500;
	}

	private HtmlResponse respond400(HtmlRequest htmlRequest) {
		HtmlResponse response400 = new HtmlResponse();

		response400.setStatus(htmlRequest.httpVersion, 400);
		response400.setContentTypeLine("text/html");
		String body400 = "<HTML><HEAD><TITLE>Bad Request</TITLE></HEAD>" +
				"<BODY><H1>Bad Request</H1><H2>You are not bad, only the request, and requests can change.</H2></BODY></HTML>";
		response400.setEntityBody(body400.getBytes());

		return response400;
	}
	
	private HtmlResponse respond403(HtmlRequest htmlRequest) {
		HtmlResponse response403 = new HtmlResponse();

		response403.setStatus(htmlRequest.httpVersion, 403);
		response403.setContentTypeLine("text/html");
		String body403 = "<HTML><HEAD><TITLE>403 Forbidden</TITLE></HEAD>" +
				"<BODY><H1>Forbidden Request</H1><H2>How can something so wrong feel so right?</H2></BODY></HTML>";
		response403.setEntityBody(body403.getBytes());

		return response403;
	}

	private boolean checkIfRequestedFileLegal(HtmlRequest htmlRequest) throws IOException {

		String requestedFileStr = htmlRequest.requestedFile;
		
		//Check if it is default
		if (requestedFileStr.equals("/")) {
			htmlRequest.requestedFile = "/" + defaultPage.toString();
			requestedFileStr = htmlRequest.requestedFile;
		}
		String requestedFileFullPath = rootDirectory.getCanonicalPath() + requestedFileStr;
		File requestedFile = new File(requestedFileFullPath);

		// Checking that the requested file path is under the root directory
		if (!requestedFile.getAbsolutePath().startsWith(rootDirectory.getCanonicalPath())) {
			return false;
		}

		// Checking if it is a directory
		if (requestedFile.isDirectory()) {
			return false;
		}

		// Check if the file exists
		if (!requestedFile.exists()) {
			return false;
		}
	
		return true;
	}

	private HtmlRequest readRequest(Socket socket) throws IOException {
		
		BufferedReader requestBufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		StringBuilder requestStringBuilder = new StringBuilder();
		try {
			String line = requestBufferedReader.readLine();
			
			while (!line.isEmpty()) {
				System.out.println(line);
				requestStringBuilder.append(line + NEWLINE);
				line = requestBufferedReader.readLine();
			}
			
		} catch (IOException e) {
			System.out.println("An error occured while reading from the socket: " + e.toString());
		}
		if(requestStringBuilder.toString().isEmpty()){
			return null;
		}
		HtmlRequest htmlRequest = new HtmlRequest(requestStringBuilder.toString());
		if (htmlRequest.type.equals("POST") || htmlRequest.type.equals("TRACE")) {
			htmlRequest.getParametersFromBody(requestBufferedReader);
		}
		return htmlRequest;
	}
	
	private void sendEntityBodyToClient(DataOutputStream socketOutputStream, HtmlResponse htmlResponse, boolean isChunked) throws IOException{
		
		byte[] content = htmlResponse.getEntityBody();
		
		if(!isChunked){
			try {
				socketOutputStream.write(content,0,content.length);
				socketOutputStream.flush();	
			} catch (IOException e) {
				System.out.println("Writing the answer caused an error" + e.toString());
			}
		} else {

			int currentIndexStart = 0;
			int currentIndexEnd = Math.min(CHUNCKED_BYTES - 1, content.length - 1);
			int lengthOfBytesSent = currentIndexEnd - currentIndexStart + 1;
	
			while (currentIndexStart < content.length - 1) {
				socketOutputStream.writeBytes(Integer.toHexString(lengthOfBytesSent) + CRLF);
				socketOutputStream.write(content, currentIndexStart, lengthOfBytesSent);
				socketOutputStream.writeBytes(CRLF);
				socketOutputStream.flush();
				
				currentIndexStart = currentIndexEnd + 1;
				currentIndexEnd = Math.min(currentIndexStart + CHUNCKED_BYTES - 1, content.length - 1);
				lengthOfBytesSent = currentIndexEnd - currentIndexStart + 1;
			}
			
			socketOutputStream.writeBytes("0"+CRLF);
			socketOutputStream.writeBytes(CRLF);
			socketOutputStream.flush();
		}
		
		
		
	}
	
	private byte[] makeTable(HashMap<String, String> map) {
		StringBuilder table = new StringBuilder();
		// if there are no parameters
		if (map.isEmpty()) {
			table.append("<!DOCTYPE html>" + "<HTML>" + "<HEAD><TITLE>"
					+ "table" + "</TITLE></HEAD>" + "<BODY>" + "<H1>"
					+ " no parameters" + "</H1><br><br><br>" + "</BODY></HTML>");
		} else {
			table.append("<!DOCTYPE html>" + "<HTML>"
					+ "<HEAD lang='en'><TITLE>" + "table" + "</TITLE></HEAD>"
					+ "<BODY>" + "<H1>"
					+ " table with parameters the client sent"
					+ "</H1><br><br><br>"
					+ "<table border ='2' style='width:30%'>");

			Iterator<String> keySetIterator = map.keySet().iterator();

			while (keySetIterator.hasNext()) {
				String key = keySetIterator.next();
				table.append("<tr>" + "<td>" + key + "</td>" + "<td>"
						+ map.get(key) + "</td>" + "</tr>");

			}

			table.append("</table>" + "</BODY></HTML>");
		}
		
		byte[] result = table.toString().getBytes();
		return result;

	}
}
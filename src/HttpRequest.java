import java.io.* ;
import java.net.* ;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Pattern;

final class HttpRequest implements Runnable
{
	final static String CRLF = "\r\n";
	final static String NEWLINE = System.lineSeparator();
	final static int CHUNCKED_BYTES = 1;
	private File rootDirectory;
	private File defaultPage;
	private int threadNumber;
	private SocketQueue socketRequestsQueue;
	public String fullPathForFile;

	// Constructor
	public HttpRequest(File rootDirectory, File defaultPage, SocketQueue socketRequestsQueue, int threadNumber)
	{
		this.rootDirectory = rootDirectory;
		this.defaultPage = defaultPage;
		this.socketRequestsQueue = socketRequestsQueue;
		this.threadNumber = threadNumber;
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

	private HtmlResponse respond200(HtmlRequest htmlRequest) throws IOException {
		HtmlResponse response200 = new HtmlResponse();
		byte[] bodyInBytes;
		
		if (htmlRequest.type.equals("TRACE")) {
			bodyInBytes = htmlRequest.unparsedRequest.getBytes();
		}else if(htmlRequest.type.equals("POST")){
			if (htmlRequest.requestedFile.equals("/params_info.html")) {
				bodyInBytes = makeTable(htmlRequest.parametersInRequestBody);
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
		} else {
			contentType = getContentTypeFromFile("");
		}
			
		response200.setContentTypeLine(contentType);

		return response200;
	}
	
	private byte[] readFileForResponse(HtmlRequest htmlRequest) throws IOException {

		if(htmlRequest.requestedFile.equals("/")){
			fullPathForFile = rootDirectory.getCanonicalPath() + "\\" + defaultPage.getName();
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

	private String getContentTypeFromFile(String requestedFile) {

		String type = "application/octet-stream";

		// We will check if the file has a type like .bmp .jpg
		String[] splitFileRequest = requestedFile.split(Pattern.quote("."));

		if (splitFileRequest.length < 2)
			return type;

		// If there is a type it is last
		String typeInRequest = splitFileRequest[splitFileRequest.length - 1];
		if (typeInRequest.equals("html") || typeInRequest.equals("htm"))
			type = "text/html";
		else if (typeInRequest.equals("bmp") || typeInRequest.equals("png") || typeInRequest.equals("jpg")
				|| typeInRequest.equals("jpeg") || typeInRequest.equals("gif"))
			type = "image";
		else if (typeInRequest.equals("ico"))
			type = "icon";

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
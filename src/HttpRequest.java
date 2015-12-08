import java.io.* ;
import java.net.* ;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.* ;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Pattern;

final class HttpRequest implements Runnable
{
	final static String CRLF = "\r\n";
	final static String newLine = System.lineSeparator();
	private File rootDirectory;
	private File defaultPage;
	private int threadNumber;
	private LinkedBlockingQueue<Socket> socketRequestsQueue;


	// Constructor
	public HttpRequest(File rootDirectory, File defaultPage, LinkedBlockingQueue<Socket> socketRequestsQueue, int threadNumber)
	{
		this.rootDirectory = rootDirectory;
		this.defaultPage = defaultPage;
		this.socketRequestsQueue = socketRequestsQueue;
		this.threadNumber = threadNumber;
		System.out.println("Created thread number: " + threadNumber);
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

			System.out.println("Thread number " + threadNumber + " waiting for queue");
			Socket socket = socketRequestsQueue.take();
			System.out.println("Thread number " + threadNumber + " took request");

			DataOutputStream socketOutputStream = new DataOutputStream(socket.getOutputStream());

			String unparsedRequest = readRequest(socket);
			//System.out.println("Unparsed: \n" + unparsedRequest);
			
			// If the request is empty than the socket was closed on the other side
			if (unparsedRequest.isEmpty()) {
				
				try {
					socket.close();
				} catch (Exception e) {
					System.out.println("Error on trying to close socket on empty request: " + e.toString());
				}
				System.out.println("Skipping to next");
				continue;
			}
			HtmlRequest htmlRequest = new HtmlRequest(unparsedRequest);
			HtmlResponse responseToClient;

			if (!htmlRequest.isLegalRequest) {
				// The request format is illegal
				responseToClient = respond400(htmlRequest);
			} else if (htmlRequest.type == null || (!htmlRequest.type.equals("GET") && !htmlRequest.equals("POST"))) {
				// The request method is unimplemented
				responseToClient = respond501(htmlRequest);
			} else {
				boolean isFileLegal = false;
				try {
					isFileLegal = checkIfRequestedFileLegal(htmlRequest.requestedFile);
				} catch (IOException e) {
					System.out.println("Error checking the file: " + htmlRequest.requestedFile);
					System.out.println(e.toString());
				}
				if (!isFileLegal) {
					System.out.println("Sending 404 to client.");
					responseToClient = respond404(htmlRequest);
				} else {
					//System.out.println("Generating 200 Response.");
					responseToClient = respond200(htmlRequest);
				}
			}

			//System.out.println("Sending response to client.");
			//System.out.println("Header of sent response:");
			System.out.println(responseToClient.getStatusLine() + responseToClient.getContentType() + 
					responseToClient.getContentLengthLine());
			try {
				// Send the status line.
				socketOutputStream.writeBytes(responseToClient.getStatusLine());
				System.out.println("Thread " + threadNumber + ": statusLine");

				// Send the content type line.
				socketOutputStream.writeBytes(responseToClient.getContentType());
				System.out.println("Thread " + threadNumber + ": contentType");

				// Send content length.
				socketOutputStream.writeBytes(responseToClient.getContentLengthLine());
				System.out.println("Thread " + threadNumber + ": contentLength");

				// Send a blank line to indicate the end of the header lines.
				socketOutputStream.writeBytes(CRLF);
				System.out.println("Thread " + threadNumber + ": EmptyLine");
				
			} catch (Exception e) {
				System.out.println("Writing the header caused an error" + e.toString());
			}

			// Send the content of the HTTP.

			try {
				socketOutputStream.write(responseToClient.getEntityBody(),0,responseToClient.getEntityBody().length);
				System.out.println("Thread " + threadNumber + ": entityBody");
				socketOutputStream.flush();	
			} catch (Exception e) {
				System.out.println("Writing the answer caused an error" + e.toString());
			}
			

			//socketOutputStream.writeBytes(responseToClient.getEntityBody()) ;

			// Close streams and socket.
			try {
				socketOutputStream.close();
				socket.close();	
			} catch (Exception e) {
				System.out.println("closing the socket caused an error");
			}
			


		}

	}

	private HtmlResponse respond200(HtmlRequest htmlRequest) throws IOException {
		HtmlResponse response200 = new HtmlResponse();
		String requestedFileFullPath;

		if(htmlRequest.requestedFile.equals("/")){
			requestedFileFullPath = rootDirectory.getCanonicalPath() + "\\index.html";
		}else{
			requestedFileFullPath = rootDirectory.getCanonicalPath() + htmlRequest.requestedFile;
		}

		File file = new File (requestedFileFullPath);
		byte [] buffer  = new byte [(int)file.length()];
		FileInputStream fis = new FileInputStream(file);
		BufferedInputStream bis = new BufferedInputStream(fis);
		bis.read(buffer,0,buffer.length);
		bis.close();

		response200.setEntityBody(buffer);
		response200.setStatus(htmlRequest.httpVersion, 200);
		String contentType = getContentTypeFromFile(htmlRequest.requestedFile);
		response200.setContentTypeLine(contentType);;

		return response200;
	}

	private String getContentTypeFromFile(String requestedFile) {

		String type = "application/octet-stream";

		// We will check if the file has a type like .bmp .jpg
		String[] splitFileRequest = requestedFile.split(Pattern.quote("."));

		if (splitFileRequest.length < 2)
			return "text/html";

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

	private boolean checkIfRequestedFileLegal(String requestedFileStr) throws IOException {

		//Check if it is default
		if (requestedFileStr.equals("/")) {
			return true;
		}
		String requestedFileFullPath = rootDirectory.getCanonicalPath() + requestedFileStr;
		System.out.println("File requested: " + requestedFileFullPath);
		File requestedFile = new File(requestedFileFullPath);

		// Checking that the requested file path is under the root directory
		if (!requestedFile.getAbsolutePath().startsWith(rootDirectory.getCanonicalPath())) {
			return false;
		}
		System.out.println("The file is in the correct directory.");
		// Check if the file exists
		if (!requestedFile.exists()) {
			return false;
		}
		System.out.println("The file exists.");
		//System.out.println("Debbuging: The requested file is: " + requestedFileFullPath);
		return true;
	}

	private String readRequest(Socket socket) throws IOException {

		BufferedReader requestBufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		StringBuilder requestStringBuilder = new StringBuilder();
		try {
			String line = requestBufferedReader.readLine();
			while (line != null && !line.isEmpty()) {
				//System.out.println(line);
				requestStringBuilder.append(line + newLine);
				line = requestBufferedReader.readLine();

			}
	
		} catch (IOException e) {
			System.out.println("An error occured while reading from the socket: " + e.toString());
		}
		
		return requestStringBuilder.toString();
	}

}



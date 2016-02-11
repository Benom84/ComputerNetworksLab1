import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.*;
import java.util.regex.Matcher;


public class ClientRequest {
	private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/535.1 (KHTML, like Gecko) Chrome/13.0.782.112 Safari/535.1";
	public final static String getRequest = "GET ";
	public final static String headRequest = "HEAD ";
	public final static int port = 80;
	public final static String CRLF = "\r\n";
	public final static String newLine = System.lineSeparator();
	public final static int TIMEOUT = 4000;
	
	public String host;
	public HashMap<String, String> responseHeaderFields;
	
	private String body;
	private String location;
	private long timeOfRttInMilliseconds;
	private String responseStatus;
	private String responseStatusCode;
	private String responseHttpVersion;
	private String[] parsedRequest;

	public ClientRequest(String url, String requestType) throws Exception {

		parseURL(url);
		Socket socket = new Socket();
		DataOutputStream socketOutputStream;
		long start = System.currentTimeMillis();
		
		try {
			socket.connect(new InetSocketAddress(host, port));
			socketOutputStream = new DataOutputStream(socket.getOutputStream());
			if (requestType.equals(headRequest)) {
				socketOutputStream.writeBytes(headRequest + location + " HTTP/1.1" + CRLF);
				socketOutputStream.writeBytes("Host: " + host + CRLF);
				socketOutputStream.writeBytes("User-Agent: " + USER_AGENT + CRLF + CRLF);
				socketOutputStream.flush();
			} else if (requestType.equals(getRequest)) {
				socketOutputStream.writeBytes("GET " + location + " HTTP/1.1" + CRLF);
				socketOutputStream.writeBytes("Host: " + host + CRLF);
				socketOutputStream.writeBytes("User-Agent: " + USER_AGENT + CRLF + CRLF);
				socketOutputStream.flush();
				while(socket.getInputStream().available() == 0){}
			}
			
		}catch (Exception e){
			System.out.println("Failed to connect to " + url);
			timeOfRttInMilliseconds = 0;
			if (socket != null) {
				try {
					socket.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
			throw e;
		}
		

		timeOfRttInMilliseconds = (System.currentTimeMillis() - start);
		StringBuilder headerResponse = new StringBuilder();
		InputStreamReader IR;
		try {
			IR = new InputStreamReader(socket.getInputStream());
			int singleChar = 0;

			//Read Header
			singleChar = IR.read();
			while (singleChar > 0) {
				headerResponse.append((char)singleChar);
				if (headerResponse.toString().endsWith(CRLF + CRLF)) {
					break;
				}

				singleChar = IR.read();
			}	
		} catch (IOException e) {
			System.out.println("Error reading header from: " + url);
			throw e;
		}
		
		parseResponse(headerResponse.toString());

		// Read Body
		StringBuilder BodyResponse = new StringBuilder();
		BufferedReader inputBuffer;
		int contentLength = 0;
		if (responseHeaderFields.containsKey("Content-Length")) {
			contentLength = Integer.parseInt(responseHeaderFields.get("Content-Length"));
			body = "";
			if ((contentLength > 0) && (requestType == getRequest)) {
				try {
					inputBuffer = new BufferedReader(IR);
					int singleChar = 0;
					int count = 1;
					socket.setSoTimeout(TIMEOUT);
					singleChar = inputBuffer.read();
					while ((singleChar != -1 || inputBuffer.ready()) && count < contentLength) {
						BodyResponse.append((char)singleChar);
						singleChar = inputBuffer.read();
						count++;
					}
				} catch(Exception e){
					System.out.println("Socket error while reading body of url:" + url);
					body = BodyResponse.toString();
					if (socket != null) {
						try {
							socket.close();
						} catch (IOException e1) {
							
						}
					}
					throw e;
				}
				body = BodyResponse.toString();

			}else{
				body="";
			}
		} else if(responseHeaderFields.containsKey("Transfer-Encoding")){
			inputBuffer = new BufferedReader(IR);
			try {
				socket.setSoTimeout(TIMEOUT);
				body = readChunkedData(inputBuffer);	
			} catch(Exception e){
				System.out.println("Socket error while reading chunked body of url:" + url);
				if (socket != null) {
					try {
						socket.close();
					} catch (IOException e1) {
						
					}
				}
				throw e;
			}
			

		}else{
			System.out.println("Error Reading body!");
			body = "";
		}



		if (socket != null)
			try {
				socket.close();
			} catch (IOException e) {

			}

	}

	private void parseResponse(String unparsedResponse){

		// Divide the string to lines
		String[] requestLines = unparsedResponse.split(newLine);
		if (requestLines.length < 1) {
			return;
		}

		String[] header = requestLines[0].split(" ");
		if (header.length < 3) {
			return;
		}

		responseHttpVersion = header[0];
		responseStatusCode = header[1];
		responseStatus = header[2];
		parsedRequest = requestLines;
		responseHeaderFields = getHeaders(parsedRequest);
	}


	private void parseURL(String url){
		//Group(1) is http:// or https://
		//Group(2) is host
		//Group(3) is location

		try {
			Matcher matcher = Crawler.DOMAIN_PATTERN.matcher(url);
			if (matcher.find()) {

				if (matcher.group(1) == null && matcher.group(2).endsWith(":")) {
					url = url + "/";
					parseURL(url);
				}
				else {
					host = matcher.group(2);
					location = matcher.group(3);
				}
			}else{
				if(!url.endsWith("/")){
					parseURL(url + "/");
				}
			}
		}catch(Exception e){
			System.out.println("Failed to parse the Url: " + url);
		}

	}

	public HashMap<String, String> getHeaders(String[] headersInString){
		HashMap<String, String> result =  new HashMap<>();
		for (String line : headersInString)
		{
			String[] splitLine = line.split(": ");
			if(splitLine.length == 2){
				result.put(splitLine[0], splitLine[1]);
			}
		}
		return result;
	}
	private String readChunkedData(BufferedReader inputBuffer) throws IOException {
		StringBuilder SB = new StringBuilder();
		while(true) {
			String line = inputBuffer.readLine();
			if (line == null) {
				throw new IOException();
			}
			if (line == CRLF) {
				continue;
			}
			int index;
			try {
				index = Integer.parseInt(line, 16);
			}catch(Exception e){
				index = -1;
			}
			if(index == 0){
				return SB.toString();
			}else if(index > 0){
				if(SB.length() > 0) {
					SB.deleteCharAt(SB.length() - 1);
					SB.deleteCharAt(SB.length() - 1);
				}
				continue;
			}else{
				SB.append(line);
			}
		}
	}

	public long getRTTtime(){
		return timeOfRttInMilliseconds;
	}
	public String getResponseStatusCode(){
		return responseStatusCode;
	}

	public String getResponseStatus(){
		return responseStatus;
	}

	public String getResponseHttpVersion(){
		return responseHttpVersion;
	}

	public String getBody() { return body; }

}
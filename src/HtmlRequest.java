import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.regex.Pattern;

public class HtmlRequest {

	protected final String newLine = System.lineSeparator();
	protected String type;
	protected String requestedFile;
	protected String httpVersion;
	protected String[] parsedRequest;
	protected HashMap<String, String> requestHeaderFields;
	protected HashMap<String, String> parametersInRequest;
	public HashMap<String, String> parametersInRequestBody;
	protected boolean isLegalRequest = false;
	protected String unparsedRequest;
	public boolean isChunked = false;

	public HtmlRequest(String unparsedRequest) {

		this.unparsedRequest = unparsedRequest;

		// Divide the string to lines
		String[] requestLines = unparsedRequest.split(newLine);
		if (requestLines.length < 1) {
			return;
		}

		String[] header = requestLines[0].split(" ");
		if (header.length < 3) {
			return;
		}

		type = header[0];
		requestedFile = header[1];
		httpVersion = header[2];
		parsedRequest = requestLines;

		requestHeaderFields = createRequestHeaderFields(parsedRequest);

		if (type.equals("POST") || type.equals("GET") ||type.equals("HEAD")) {
			if(requestedFile.contains("?")){
				String[] parameters = requestedFile.split(Pattern.quote("?"));
				parametersInRequest = getParametersFromURL(parameters[1]);
				//System.out.println("******Debbug parameters: " + System.lineSeparator());
				//System.out.println(parametersInRequest.toString());
				//System.out.println("******End debbuging parmeters.");

			}
		}

		//System.out.println("The size of hashmap is: " + requestHeaderFields.size());
		//System.out.println("The value of Connection is: " + requestHeaderFields.get("Connection"));
		if(requestedFile.contains("/../")){
			isLegalRequest = false;
		}else{
			isLegalRequest = true;
		}
	}

	private HashMap<String, String> createRequestHeaderFields(String[] list){
		HashMap<String, String> result = new HashMap<String, String>();
		for(int i = 1; i < list.length; i++){
			String[] line = list[i].split(": ");
			result.put(line[0].toUpperCase(), line[1]);
		}
		return result;
	}

	private HashMap<String, String> getParametersFromURL(String parameters){
		if(!parameters.contains("=")){
			System.out.println("Error: No Parameters to extract.");
			return null;
		}else{
			HashMap<String, String> result = new HashMap<String, String>();
			String[] parm = parameters.split("&");
			for(int i = 0; i < parm.length; i++){
				String[] parmeter = parm[i].split("=");
				result.put(parmeter[0], parmeter[1]);
			}
			return result;
		}
	}
	public void getParametersFromBody(BufferedReader requestReader) throws IOException{

		//String lengthStr;
		String postBody;
		int bodyLength;
		char[] byteLoad;
		//System.out.println("****Debug: Got Here!*****");
		String lengthStr = requestHeaderFields.get("CONTENT-LENGTH");
		//System.out.println("****Debug: content length: " + lengthStr);
		if (lengthStr != null) {

			try {
				bodyLength = Integer.parseInt(lengthStr);
			} catch (NumberFormatException e) {
				throw new IOException();
			}
			System.out.println("*****Debbug: body of request length: " + bodyLength);
			// body empty
			if (bodyLength == 0) {
				return;
			}
			byteLoad = new char[bodyLength];

			try {
				requestReader.read(byteLoad, 0, bodyLength);
			} catch (IOException e) {
				System.out.println("Error reading from buffered reader again");
				throw e;
			}
			
			parametersInRequestBody = new HashMap<>();
			postBody = new String(byteLoad);
			
			unparsedRequest = unparsedRequest.concat(newLine + newLine + postBody);
			System.out.println("String body is: " + postBody);
			String[] split = postBody.split("&");
			for (String pair : split) {
				// split name and value.
				String splitPair[] = pair.split("=");

				String key;
				String value;
				int index = 1;

				try {

					key = URLDecoder.decode(splitPair[0], "UTF-8").trim();
					if (splitPair.length == 1) {
						value = "";
					} else {
						value = URLDecoder.decode(splitPair[1], "UTF-8").trim();	
					}

					System.out.println("Key is: " + key + " Value is: " + value);
					// if the map doesn't contain this key.
					if (!parametersInRequestBody.containsKey(key)) {
						parametersInRequestBody.put(key, value);
						System.out.println("Index: " + index + " key: " + key + " value: " + value);
						index++;
					}

				} catch (UnsupportedEncodingException e) {
					// couldn't parse the parameter.
					continue;
				}

				//}
			}
		}
	}



}
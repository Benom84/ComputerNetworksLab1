import java.util.HashMap;

public class HtmlRequest {

	protected final String newLine = System.lineSeparator();
	protected String type;
	protected String requestedFile;
	protected String httpVersion;
	protected String[] parsedRequest;
	protected HashMap<String, String> requestHeaderFields;
	protected boolean isLegalRequest = false;
	
	public HtmlRequest(String unparsedRequest) {
		
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
		//System.out.println("The size of hashmap is: " + requestHeaderFields.size());
		//System.out.println("The value of Connection is: " + requestHeaderFields.get("Connection"));
		
		isLegalRequest = true;
	}
	
	private HashMap<String, String> createRequestHeaderFields(String[] list){
		HashMap<String, String> result = new HashMap<String, String>();
		for(int i = 1; i < list.length; i++){
			String[] line = list[i].split(": ");
			result.put(line[0], line[1]);
		}
		return result;
	}
	
}

//GET /index.html HTTP/1.1
//Host: 127.0.0.1:8080
//Connection: keep-alive
//Cache-Control: max-age=0
//Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8
//Upgrade-Insecure-Requests: 1
//User-Agent: Mozilla/5.0 (Windows NT 6.3; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/46.0.2490.86 Safari/537.36
//Accept-Encoding: gzip, deflate, sdch
//Accept-Language: en-US,en;q=0.8,he;q=0.6
//GET /index.html HTTP/1.1
//Host: 127.0.0.1:8080
//Connection: keep-alive
//Cache-Control: max-age=0
//Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8
//Upgrade-Insecure-Requests: 1
//User-Agent: Mozilla/5.0 (Windows NT 6.3; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/46.0.2490.86 Safari/537.36
//Accept-Encoding: gzip, deflate, sdch
//Accept-Language: en-US,en;q=0.8,he;q=0.6
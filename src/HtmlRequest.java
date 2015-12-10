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
		
		if(requestedFile.contains("?")){
			String[] parameters = requestedFile.split(Pattern.quote("?"));
			parametersInRequest = getParameters(parameters[1]);
			//System.out.println("******Debbug parameters: " + System.lineSeparator());
			//System.out.println(parametersInRequest.toString());
			//System.out.println("******End debbuging parmeters.");
			
		}
		/*
		if((parametersInRequest.get("CHUNKED") != null) && (parametersInRequest.get("CHUNKED").toLowerCase().equals("yes"))){
			
					isChunked = true;	
		}
		*/
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
	
	private HashMap<String, String> getParameters(String parameters){
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
	
	
	
}

public class HtmlResponse {

	final static String CRLF = "\r\n";
	final static String Description200 = "OK";
	final static String Description404 = "Not Found";
	final static String Description501 = "Not Implemented";
	final static String Description400 = "Bad Request";
	final static String Description500 = "Internal Sever Error";
	private String statusLine;
	private String contentTypeLine;
	private String entityBody;


	public void setStatus(String httpVersion, int responseCode) {

		String responseCodeStatus;
		switch (responseCode){
		case 200: 	responseCodeStatus = Description200;
					break;
		case 400: 	responseCodeStatus = Description400;
					break;
		case 404: 	responseCodeStatus = Description404;
					break;
		case 500: 	responseCodeStatus = Description500;
					break;
		case 501: 	responseCodeStatus = Description501;
					break;
		default: 	responseCodeStatus = "Other";
					break;
		}
		
		statusLine = httpVersion + " " + responseCode + " " + responseCodeStatus + CRLF;
	}

	public void setContentTypeLine(String contentType) {
		
		contentTypeLine = "Content-Type: " + contentType + CRLF;

	}

	public void setEntityBody(String body) {
		entityBody = body;
	}

	public String getContentLengthLine() {
		return "Content-Length: " + entityBody.length() + CRLF;
	}

	public String getStatusLine() {
		return statusLine;
	}
	
	public String getEntityBody() {
		return entityBody;
	}
	
	public String getContentType() {
		return contentTypeLine;
	}

}

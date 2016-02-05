
public class HTMLContent {
	private String html;
	private String sourceWebPage;
	
	public HTMLContent(String html, String source) {
		this.html = html;
		this.sourceWebPage = source;
	}
	
	public String GetHTML() {
		return html;
	}
	
	public String GetSource() {
		return sourceWebPage;
	}
}


public class HTMLContent {
	private String html;
	private String sourceWebPage;
	private String urlSource;
	
	public HTMLContent(String html, String source, String urlSource) {
		this.html = html;
		this.sourceWebPage = source;
		this.urlSource = urlSource;
	}
	
	public String GetHTML() {
		return html;
	}
	
	public String GetSource() {
		return sourceWebPage;
	}
	
	public String GetURLSource() {
		return urlSource;
	}
}

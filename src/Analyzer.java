import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Analyzer implements Runnable {

	private Crawler parentCrawler;
	private Boolean running;
	private HTMLContent htmlContent;

	public Analyzer(Crawler crawler) {

		parentCrawler = crawler;
	}

	@Override
	public void run() { 
		running = true;
		while (running) {
			try {
				htmlContent = parentCrawler.nextHtmlToAnalyze();
			} catch (InterruptedException e1) {
				//e1.printStackTrace();
			}

			if (!running) {
				System.out.println("Analyzer is shutting down.");
				return;
			}

			if (htmlContent != null) {
				System.out.println("Activating parseHTML");
				try {
					parseHtml();
					int temp = parentCrawler.lowerWorkload();
					System.out.println("#################Analyzer finished analyzing and workload is now: " + temp);
				} catch (IOException e) {
					System.out.println("Could not parseHTML.");
				}
			}	
		}
	}

	private void parseHtml() throws IOException {

		HTMLContent currentHtmlToParse = htmlContent;

		Set<String> allLinksInHtml = getLinksFromHtml(currentHtmlToParse.GetHTML());
		for (String currentLink : allLinksInHtml) {
			try {
				if (isURLRelative(currentLink)) {
					parentCrawler.addUrlToDownload(currentHtmlToParse.GetSource() + currentLink);	
				}
				else {
					if (isPartOfHost(currentLink)) {
						parentCrawler.addUrlToDownload(currentLink);
						parentCrawler.addUrlToInternal(currentLink);
					} else {
						parentCrawler.addURLToExternal(currentLink);
					}

				}

			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		htmlContent = null;
	}

	private boolean isPartOfHost(String currentLink) {
		Pattern domainPattern = Pattern.compile("(.*\\.)?("+ parentCrawler.targetURL + ").*");
		Matcher matcher = domainPattern.matcher(currentLink);
		if (matcher.find()) {
			return true; 
		} else {
			return false;
		}

	}
	/*
	public Set<String> getLinksFromHtml(String HTMLPage){

		//System.out.println("Analyzer is parsing: " + HTMLPage);
		Pattern linkPattern = Pattern.compile("href=\".*?(http:\\/\\/)*(.*?)\"",  Pattern.CASE_INSENSITIVE|Pattern.DOTALL);
		Matcher pageMatcher = linkPattern.matcher(HTMLPage);
		Set<String> links = new HashSet<>();
		while(pageMatcher.find()){
			links.add(pageMatcher.group(2));
			System.out.println("Link from analyzer: " + pageMatcher.group(2));
		}

		return links;
	}
	 */
	public Set<String> getLinksFromHtml(String HTMLPage) throws IOException {

		//System.out.println("Analyzer is parsing: " + HTMLPage);
		Pattern linkPattern = Pattern.compile("href= *' *(.*?)'|href= *\" *(.*?)\"|src= *' *(.*?)'");
		Matcher pageMatcher = linkPattern.matcher(HTMLPage);
		Set<String> links = new HashSet<>();

		int index = 0;
		while(pageMatcher.find()){
			if(pageMatcher.group(1) != null) {
				if(isLinkValid(pageMatcher.group(1))) {
					links.add(pageMatcher.group(1));
					System.out.println("Link from analyzer: " + pageMatcher.group(1));
				}else{
					System.out.println("Excluded link from analyzer: " + pageMatcher.group(1));
				}
				index++;
			}
			if(pageMatcher.group(2) != null){
				if(isLinkValid(pageMatcher.group(2))) {
					links.add(pageMatcher.group(2));
					System.out.println("Link from analyzer: " + pageMatcher.group(2));
				}else{
					System.out.println("Excluded link from analyzer: " + pageMatcher.group(2));
				}
				index++;
			}
			if(pageMatcher.group(3) != null){
				if(isLinkValid(pageMatcher.group(3))) {
					links.add(pageMatcher.group(3));
					System.out.println("Link from analyzer: " + pageMatcher.group(3));
				}else{
					System.out.println("Excluded link from analyzer: " + pageMatcher.group(3));
				}
				index++;
			}
			//System.out.println("Link from analyzer: " + pageMatcher.group(2));
		}

		System.out.println("Number of links extracted: " + index);
		return links;
	}
	public static boolean isLinkValid(String link){
		if(link.startsWith("//")){
			return true;
		}
		if(link.startsWith("android-app:")){
			return false;
		}
		if(link.startsWith("javascript:")){
			return false;
		}
		if(link.startsWith("\"+")){
			return false;
		}
		if(link.endsWith(".js")){
			return false;
		}
		if(link.startsWith("#")){
			return false;
		}
		if(link.contains("twitter") ||link.contains("Twitter")){
			return false;
		}
		return true;
	}

	private boolean isURLRelative(String url) {

		if (url.trim().length() > 0) {
			return url.substring(0, 1).equalsIgnoreCase("/");	
		}
		
		return false;
		
	}

	public void shutdown() {
		running = false;
	}

}

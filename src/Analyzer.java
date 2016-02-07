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
				//System.out.println("Activating parseHTML");
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
				
				System.out.println("Analyzer: parseHTML: currentLink is: " + currentLink);
				if (isURLRelative(currentLink)) {
					System.out.println("Analyzer: parseHTML: currentLink is relative");
					parentCrawler.addUrlToDownload(currentHtmlToParse.GetSource() + currentLink);	
				}
				else {
					if (isPartOfHost(currentLink)) {
						System.out.println("Analyzer: parseHTML: currentLink is non relative but part of the domain");
						parentCrawler.addUrlToDownload(currentLink);
						parentCrawler.addUrlToInternal(currentLink);
					} else {
						parentCrawler.addURLToExternal(currentLink);
						System.out.println("Analyzer: parseHTML: currentLink is non relative and not part of the domain");
					}

				}

			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		htmlContent = null;
	}

	private boolean isPartOfHost(String currentLink) {

		//System.out.println("Analyzer: isPartOfHost: got link: " + currentLink);
		Matcher domainMatcher = Crawler.DOMAIN_PATTERN.matcher(currentLink);
		if (domainMatcher.find()) {
			System.out.println("Analyzer: isPartOfHost: domain matcher found");
			if (domainMatcher.group(1) == null && domainMatcher.group(2).endsWith(":")) {
				String link = currentLink + "/";
				return isPartOfHost(link);
			}
			String domainInCurrentLink = domainMatcher.group(2);	
			System.out.println("Analyzer: isPartOfHost: domain is: " + domainInCurrentLink);
			if (domainInCurrentLink != null) {
				Pattern domainPattern = Pattern.compile("(.*\\.)?("+ parentCrawler.targetURL + ")");
				System.out.println("Analyzer: isPartOfHost: domain pattern is: " + domainPattern.toString());
				Matcher matcher = domainPattern.matcher(domainInCurrentLink);
				if (matcher.find()) {
					System.out.println("Analyzer: isPartOfHost: domain pattern found");
					return true; 
				} 
			} 
		}


		return false;




	}

	public Set<String> getLinksFromHtml(String HTMLPage) throws IOException {

		//System.out.println("Analyzer is parsing: " + HTMLPage);
		Pattern linkPattern = Pattern.compile("href= *' *(.*?)'|href= *\" *(.*?)\"|src= *' *(.*?)'");
		Matcher pageMatcher = linkPattern.matcher(HTMLPage);
		Set<String> links = new HashSet<>();

		int index = 0;
		while(pageMatcher.find()){
			String linkFound;
			for (int i = 1; i <= 3; i++) {
				if(pageMatcher.group(i) != null) {
					linkFound = pageMatcher.group(i);
					linkFound = removeParamsFromLink(linkFound);
					if(isLinkValid(linkFound)) {
						links.add(linkFound);
						System.out.println("Link from analyzer: " + linkFound);
					}else{
						System.out.println("Excluded link from analyzer: " + linkFound);
					}
					index++;
					break;
				}
			}
			
			//System.out.println("Link from analyzer: " + pageMatcher.group(2));
		}

		System.out.println("Number of links extracted: " + index);
		return links;
	}
	private String removeParamsFromLink(String linkFound) {
		int questionMarkIndex = linkFound.indexOf("?");
		if (questionMarkIndex > -1) {
			System.out.println("Analyzer: removeParamsFromLink: returning " + linkFound.substring(0, questionMarkIndex));
			return linkFound.substring(0, questionMarkIndex);
		}
		return linkFound;
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

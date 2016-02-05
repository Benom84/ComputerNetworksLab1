import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Analyzer implements Runnable {

	private Crawler parentCrawler;

	public Analyzer(Crawler crawler) {

		parentCrawler = crawler;

	}

	@Override
	public void run() {

		HTMLContent currentHtmlToParse;
		while (true) {

			try {
				currentHtmlToParse = parentCrawler.nextHtmlToAnalyze();
				parentCrawler.AdjustWorkingThreadCount(1);
			} catch (InterruptedException e) {
				e.printStackTrace();
				continue;
			}

			if (currentHtmlToParse == null) {
				parentCrawler.AdjustWorkingThreadCount(-1);
				return;
			}
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
			parentCrawler.AdjustWorkingThreadCount(-1);

		}

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

	private boolean isURLRelative(String url) {

		return url.substring(0, 1).equalsIgnoreCase("/");
	}

}

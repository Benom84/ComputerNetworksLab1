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
		
		String currentHtmlToParse;
		while (true) {
			
			try {
				currentHtmlToParse = parentCrawler.nextHtmlToAnalyze();
				parentCrawler.AdjustWorkingThreadCount(1);
			} catch (InterruptedException e) {
				e.printStackTrace();
				continue;
			}
			
			Set<String> allLinksInHtml = getLinkesFromHtml(currentHtmlToParse);
			for (String currentLink : allLinksInHtml) {
				try {
					parentCrawler.addUrlToDownload(currentLink);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			parentCrawler.AdjustWorkingThreadCount(-1);
			
		}
		
	}
	
	public Set<String> getLinkesFromHtml(String HTMLPage){

        Pattern linkPattern = Pattern.compile("href=\\'.*?(http:\\/\\/)*(.*?)\\'",  Pattern.CASE_INSENSITIVE|Pattern.DOTALL);
        Matcher pageMatcher = linkPattern.matcher(HTMLPage);
        Set<String> links = new HashSet<>();
        while(pageMatcher.find()){
            links.add(pageMatcher.group(2));
            System.out.println(pageMatcher.group(2));
        }

        return links;
    }

}

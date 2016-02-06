import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Analyzer implements Runnable {

	private Crawler parentCrawler;
	private Boolean running;
	private HTMLContent htmlContent;
	private SynchronizedQueue<ThreadConnection<Analyzer>> availableThreads;
	private SynchronizedSet<ThreadConnection<Analyzer>> workingThreads;
	private ThreadConnection<Analyzer> threadedConnection;
	private String htmlToParse = "";

	public Analyzer(Crawler crawler, 
			SynchronizedQueue<ThreadConnection<Analyzer>> availableThreads, 
			SynchronizedSet<ThreadConnection<Analyzer>> workingThreads) {

		parentCrawler = crawler;
		this.availableThreads = availableThreads;
		this.workingThreads = workingThreads;
	}

	@Override
	public void run() { 
		running = true;
		while (running) {
			if (htmlContent != null) {
				System.out.println("Activating parseHTML");
				parseHtml();
			}	

			System.out.println("Analyzer: going to sleep.");
			synchronized(htmlToParse) {
				try {

					while (htmlContent == null)
						htmlToParse.wait(100);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			System.out.println("Analyzer: Woken up from wait.");
		}
	}

	public void SetHTML(HTMLContent htmlContent, ThreadConnection<Analyzer> workerThread) {
		synchronized (htmlToParse) {
			this.threadedConnection = workerThread;
			this.htmlContent = htmlContent;
			this.htmlToParse = htmlContent.GetHTML();
			System.out.println("Analyzer was set");
			htmlToParse.notifyAll();
		}

	}

	private void parseHtml() {

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
		htmlToParse = "";
		try {
			availableThreads.put(threadedConnection);
			workingThreads.remove(threadedConnection);
		} catch (InterruptedException e) {
			e.printStackTrace();
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

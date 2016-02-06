import java.io.IOException;
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
				try {
					parseHtml();
				} catch (IOException e) {
					System.out.println("Could not parseHTML.");
				}
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
			return false;
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
		return true;
	}

	private boolean isURLRelative(String url) {

		return url.substring(0, 1).equalsIgnoreCase("/");
	}



}

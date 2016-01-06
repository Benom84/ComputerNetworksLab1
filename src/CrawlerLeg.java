import java.io.IOException;
import java.net.HttpURLConnection;

import java.util.LinkedList;
import java.util.List;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class CrawlerLeg {

	// We'll use a fake USER_AGENT so the web server thinks the robot is a normal web browser.
	private static final String USER_AGENT =
			"Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/535.1 (KHTML, like Gecko) Chrome/13.0.782.112 Safari/535.1";
	private List<String> links = new LinkedList<String>();
	//private Document htmlDocument;
	int numberOfImages = 0;
	private int totalSizeOfImages = 0;
	private int numberOfVideos = 0;
	private int totalSizeOfVideos = 0;
	private int numberOfDocuments = 0;
	private int totalSizeOfDocuments = 0;
	private int numberOfPages = 0;
	private int totalSizeOfPages = 0;

	public boolean crawl(Crawler crawler ,String url)
	{

			try
	        {
	        	//TODO: create a connection and get the body from the connection if response is 200

				Connection connection = Jsoup.connect(url);
				int statusCode = connection.response().statusCode();
	            if(statusCode == 200) // 200 is the HTTP OK status code
	            {
					System.out.println("\n**Visiting** Received web page at " + url);
					if(connection.response().contentType().contains("text/html")){
						Document htmlDocument = connection.get();
						Elements linksOnPage = htmlDocument.select("a[href]");
						for(Element link : linksOnPage)
						{
							this.links.add(link.absUrl("href"));
						}
					}else if(isValidExtension(crawler.getImageExtensions(), url)){
						String sizeAsString =connection.response().header("Content-Length");
						int sizeAsInt = Integer.parseInt(sizeAsString);
						totalSizeOfImages += sizeAsInt;
						numberOfImages++;
					}else if(isValidExtension(crawler.getVideoExtensions(), url)){
						String sizeAsString = connection.response().header("Content-Length");
						int sizeAsInt = Integer.parseInt(sizeAsString);
						totalSizeOfVideos += sizeAsInt;
						numberOfVideos++;
					}else if(isValidExtension(crawler.getImageExtensions(), url)){
						String sizeAsString = connection.response().header("Content-Length");
						int sizeAsInt = Integer.parseInt(sizeAsString);
						totalSizeOfDocuments += sizeAsInt;
						numberOfDocuments++;
					}else{
						String sizeAsString = connection.response().header("Content-Length");
						int sizeAsInt = Integer.parseInt(sizeAsString);
						totalSizeOfPages += sizeAsInt;
						numberOfPages++;
					}
					return true;
	            }else{
					System.out.println("\n**Error** got bad response from " + url);
				}

	            return true;
	        }
	        catch(IOException ioe) {
				// We were not successful in our HTTP request
				return false;
			}
	}

	public List<String> getLinks()
	{
		return this.links;
	}

	public int getNumberOfImages(){
		return numberOfImages;
	}

	public int getNumberOfVideos(){
		return numberOfVideos;
	}

	public int getNumberOfDocuments(){
		return numberOfDocuments;
	}

	public int getNumberOfPages(){
		return numberOfPages;
	}

	public int getTotalSizeOfImages(){
		return totalSizeOfImages;
	}

	public int getTotalSizeOfVideos(){
		return totalSizeOfVideos;
	}

	public int getTotalSizeOfDocuments(){
		return totalSizeOfDocuments;
	}

	public int getTotalSizeOfPages(){
		return totalSizeOfPages;
	}

	private boolean isValidExtension(List<String> extensions, String url){
		int index = url.lastIndexOf('.');
		if(index == -1){
			return false;
		}
		index++;
		String extensionFromUrl = url.substring(index, url.length()).toLowerCase();
		for (String extension : extensions)
		{
			if(extensionFromUrl.equals(extension)){
				return true;
			}
		}
		return false;
	}
}


import java.io.IOException;
import java.net.HttpURLConnection;

import java.util.LinkedList;
import java.util.List;


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


	    /**
	     * This performs all the work. It makes an HTTP request, checks the response, and then gathers
	     * up all the links on the page. 
	     * 
	     * @param url
	     *            - The URL to visit
	     * @return whether or not the crawl was successful
	     */
	    public boolean crawl(String url)
	    {
	        /*
			try
	        {
	        	//TODO: create a connection and get the body from the connection if response is 200
				HttpURLConnection connection = null;


	            if(httpRequest == 200) // 200 is the HTTP OK status code
	            {
	                System.out.println("\n**Visiting** Received web page at " + url);
	            }
	            if(!connection.response().contentType().contains("text/html"))
	            {
	                System.out.println("**Failure** Retrieved something other than HTML");
	                return false;
	            }
	            Elements linksOnPage = htmlDocument.select("a[href]");
	            System.out.println("Found (" + linksOnPage.size() + ") links");
	            for(Element link : linksOnPage)
	            {
	                this.links.add(link.absUrl("href"));
	            }
	            return true;
	        }
	        catch(IOException ioe)
	        {
	            // We were not successful in our HTTP request
	            return false;
	        } */
			//TODO: delete this row
	    return false;
		}


	    /**
	     * Performs a search on the body of on the HTML document that is retrieved. This method should
	     * only be called after a successful crawl.
	     * 
	     * @param searchWord
	     *            - The word or string to look for
	     * @return whether or not the word was found
	     */
	    /*
	    public boolean searchForWord(String searchWord)
	    {
	        // Defensive coding. This method should only be used after a successful crawl.
	        if(this.htmlDocument == null)
	        {
	            System.out.println("ERROR! Call crawl() before performing analysis on the document");
	            return false;
	        }
	        System.out.println("Searching for the word " + searchWord + "...");
	        String bodyText = this.htmlDocument.body().text();
	        return bodyText.toLowerCase().contains(searchWord.toLowerCase());
	    }
	    */

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

	}

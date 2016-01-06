import java.io.*;
import java.net.*;
import java.util.HashMap;


public class WebServer {

	private static String configFile = "config.ini";
	private static String serverDefaultRoot = "c:\\serverroot\\";
	private static String serverDefaultPage = "index.html";
	private static int serverDefaultPort = 8080;
	private static int serverDefaultMaxThreads = 10;
	private static String configurationSeperator = "=";
	private static String rootKey = "root";
	private static String portKey = "port";
	private static String defaultPageKey = "defaultPage";
	private static String maxThreadsKey = "maxThreads";
	private static String maxDownloadersKey = "maxDownloaders";
	private static String maxAnalyzersKey = "maxAnalyzers";
	private static String imageExtensionsKey = "imageExtensions";
	private static String videoExtensionsKey = "videoExtensions";
	private static String documentExtensionsKey = "documentExtensions";
	private static int serverDefaultMaxDownloaders = 10;
	private static int serverDefaultMaxAnalyzers = 2;
	private static String serverDefaultImageExtensions = "bmp, jpg, png, gif, ico";
	private static String serverDefaultVideoExtensions = "avi, mpg, mp4, wmv, mov, flv, swf, mkv";
	private static String serverDefaultDocumentExtensions = "pdf, doc, docx, xls, xlsx, ppt, pptx";
	private static String newLine = System.lineSeparator();
	


	public static void main(String[] args) {

		HashMap<String, String> configuration = null;
		int port;
		int maxThreads;
		File root;
		File defaultPage;

		System.out.println("Reading configuration file...");

		try {
			configuration = readServerConfiguration(configFile);
		} catch (IOException e) {
			System.out.println("Error reading the configuration file: " + configFile);
			System.out.println(e.toString());
		}

		if (configuration == null) {
			System.out.println("Using default values");
			configuration = new HashMap<>();
			configuration.put(portKey, Integer.toString(serverDefaultPort));
			configuration.put(rootKey, serverDefaultRoot);
			configuration.put(maxThreadsKey, Integer.toString(serverDefaultMaxThreads));
			configuration.put(portKey, Integer.toString(serverDefaultPort));
			configuration.put(maxDownloadersKey, Integer.toString(serverDefaultMaxDownloaders));
			configuration.put(maxAnalyzersKey, Integer.toString(serverDefaultMaxAnalyzers));
			configuration.put(imageExtensionsKey, serverDefaultImageExtensions);
			configuration.put(videoExtensionsKey, serverDefaultVideoExtensions);
			configuration.put(documentExtensionsKey, serverDefaultDocumentExtensions);
		}

		String configurationCheckResults = configurationCheck(configuration);
		String crawlerConfigurationCheckResults = crawlerConfigurationCheck(configuration);
		if (!configurationCheckResults.isEmpty()) {
			System.out.println(configurationCheckResults);
			
		}
		if (!crawlerConfigurationCheckResults.isEmpty()) {
			System.out.println(crawlerConfigurationCheckResults);
			
		}
		if (!checkForBasicConfiguration(configuration)) {
			System.out.println("Errors configuring sever. Exiting!");
			System.exit(1);	
		}

		System.out.println("Configuration successfully loaded.");
		
		// Copy crawler configuration to a different map
		HashMap<String, String> crawlerConfiguration = createCrawlerConfiguration(configuration);

		// Load the configuration set
		port = Integer.parseInt(configuration.get(portKey));
		maxThreads = Integer.parseInt(configuration.get(maxThreadsKey));
		root = new File(configuration.get(rootKey));
		defaultPage = new File(configuration.get(defaultPageKey));

		// Establish the listen socket.
		ServerSocket socket = null;
		
		try {
			socket = new ServerSocket(port);
		} catch (IOException e) {
			System.out.println("Socket could not be created." + newLine + e.toString());
			System.exit(1);
		}
		
		System.out.println("Listening port : " + port);
		SocketQueue socketRequestsQueue = new SocketQueue();
		
		Crawler crawler = new Crawler(crawlerConfiguration);
		Thread[] htmlResponseThreads = new Thread[maxThreads - 1];
		
		for (int i = 0; i < htmlResponseThreads.length; i++) {
			htmlResponseThreads[i] = new Thread(new HttpRequest(root, defaultPage, socketRequestsQueue, i, crawler));
			htmlResponseThreads[i].start();
		}

		while (true) {

			// Listen for a TCP connection request.
			Socket connection = null;
			try {

				connection = socket.accept();
				System.out.println("The HTTP request arriving to the server.");
			} catch (IOException e) {
				System.out.println("There was an error opening a socket on the server. Exiting!");
				System.exit(1);
			}

			// Construct an object to process the HTTP request message.
			try {
				socketRequestsQueue.put(connection);
			} catch (InterruptedException e) {
				System.out.println("socketRequestsQueue threw: " + e.toString());
			}
		}
	}

	private static HashMap<String, String> createCrawlerConfiguration(HashMap<String, String> configuration) {
		
		HashMap<String, String> crawlerConfiguration = new HashMap<String, String>();
		crawlerConfiguration.put(maxDownloadersKey, configuration.get(maxDownloadersKey));
		crawlerConfiguration.put(maxAnalyzersKey, configuration.get(maxAnalyzersKey));
		crawlerConfiguration.put(imageExtensionsKey, configuration.get(imageExtensionsKey));
		crawlerConfiguration.put(videoExtensionsKey, configuration.get(videoExtensionsKey));
		crawlerConfiguration.put(documentExtensionsKey, configuration.get(documentExtensionsKey));
		
		return crawlerConfiguration;
		
	}

	private static boolean checkForBasicConfiguration(HashMap<String, String> configuration) {

		return (configuration.containsKey(defaultPageKey) && configuration.containsKey(rootKey) &&
				configuration.containsKey(portKey) && configuration.containsKey(maxThreadsKey));
	}

	private static String configurationCheck(HashMap<String, String> configuration) {

		StringBuilder result = new StringBuilder();

		// Checking the port
		result.append(configurationValueCheck(configuration, portKey, serverDefaultPort));

		// Checking the maxThreads
		result.append(configurationValueCheck(configuration, maxThreadsKey, serverDefaultMaxThreads));

		// Checking the root directory
		result.append(configurationValueCheck(configuration, rootKey, serverDefaultRoot));

		// Checking the default page
		result.append(configurationValueCheck(configuration, defaultPageKey, serverDefaultPage));


		return result.toString();
	}
	
	private static String crawlerConfigurationCheck(HashMap<String, String> configuration) {

		StringBuilder result = new StringBuilder();

		// Checking the maxDownloaders
		result.append(configurationValueCheck(configuration, maxDownloadersKey, serverDefaultMaxDownloaders));

		// Checking the maxAnalyzers
		result.append(configurationValueCheck(configuration, maxAnalyzersKey, serverDefaultMaxAnalyzers));

		// Checking the imageExtensions
		result.append(configurationValueCheck(configuration, imageExtensionsKey, serverDefaultImageExtensions));

		// Checking the videoExtensions
		result.append(configurationValueCheck(configuration, videoExtensionsKey, serverDefaultVideoExtensions));
		
		// Checking the documentExtensions
		result.append(configurationValueCheck(configuration, documentExtensionsKey, serverDefaultDocumentExtensions));


		return result.toString();
	}

	private static String configurationValueCheck(HashMap<String, String> configuration, String keyToCheck,
			String defaultValue) {

		StringBuilder result = new StringBuilder();

		// If the configuration does not contain the relevant key
		if (!configuration.containsKey(keyToCheck)) {
			configuration.put(keyToCheck, defaultValue);
			result.append(keyToCheck + " was not found in the configuration file. "
					+ " Using default value: " + defaultValue + newLine);
		} else {

			String valueFromConfiguration = configuration.get(keyToCheck);
			if (keyToCheck.equals(rootKey)) {
				result.append(rootDirectoryCheck(configuration, valueFromConfiguration, defaultValue));
			}
			else if (keyToCheck.equals(defaultPageKey)) {
				// If there is no root directory defined then we can't check the page
				if (!configuration.containsKey(rootKey)) {
					result.append("Root directory is not defined. Can't check default page." + newLine);
					configuration.remove(defaultPageKey);
				} else {
					result.append(defaultPageCheck(configuration, valueFromConfiguration, defaultValue));
				}
			}

		}

		return result.toString();
	}

	private static String defaultPageCheck(HashMap<String, String> configuration, 
			String valueFromConfiguration, String defaultValue) {

		StringBuilder result = new StringBuilder();
		String pageDirectory = configuration.get(rootKey);
		File defaultPage = new File(pageDirectory + "\\" + valueFromConfiguration);

		// If the root does not exist create it
		if (!defaultPage.exists()) {
			result.append("The default page: " + valueFromConfiguration + " does not exist. Checking for default." + newLine);
			defaultPage = new File(pageDirectory + "\\" + defaultValue);
			if (!defaultPage.exists()) {
				result.append("Default page also does not exists." + newLine);
			}
		} else if (!defaultPage.isFile()) {
			result.append("The default page: " + valueFromConfiguration + " is not a file." + newLine);
		}
		return result.toString();
	}

	private static String rootDirectoryCheck(HashMap<String, String> configuration, 
			String valueFromConfiguration, String defaultRoot) {

		StringBuilder result = new StringBuilder();
		File rootDirectory = new File(valueFromConfiguration);

		// If the root does not exist create it
		if (!rootDirectory.exists()) {
			result.append("The root directory: " + valueFromConfiguration + " does not exist. Trying to create." + newLine);
			boolean directoryCreated = false;

			try {

				directoryCreated = rootDirectory.mkdirs();
				result.append("The directory created successfully." + newLine);

			} catch (Exception e) {

				result.append("Could not create root directory: " + valueFromConfiguration + newLine);
				result.append(e.toString());

			}

			// If the root was not created try the default value
			if (!directoryCreated) {

				result.append("Trying to create default root directory: " + defaultRoot + newLine);
				rootDirectory = new File(defaultRoot);
				result.append("The directory created successfully." + newLine);

				try {

					directoryCreated = rootDirectory.mkdirs();
					configuration.remove(rootKey);
					configuration.put(rootKey, defaultRoot);

				} catch (Exception e) {

					result.append("Could not create root directory: " + defaultRoot + newLine);
					result.append(e.toString());
				}
			}

			// If no directory created by this point remove the key
			if (!directoryCreated) {
				configuration.remove(rootKey);
			}
		} else if (!rootDirectory.isDirectory()) {
			result.append("Given root directory is not a directory." + newLine);
			configuration.remove(rootKey);
		}

		return result.toString();
	}

	private static String configurationValueCheck(HashMap<String, String> configuration, String keyToCheck,
			int defaultValue) {

		StringBuilder result = new StringBuilder();

		// If the configuration does not contain the relevant key
		if (!configuration.containsKey(keyToCheck)) {
			configuration.put(keyToCheck, Integer.toString(defaultValue));
			result.append(keyToCheck + " was not found in the configuration file. "
					+ " Using default value: " + defaultValue + newLine);
		} else { 
			String valueFromConfiguration = configuration.get(keyToCheck);
			int valueAsNumber = 0;
			try {
				valueAsNumber = Integer.parseInt(valueFromConfiguration);
				if (valueAsNumber < 2) {
					result.append(valueFromConfiguration + " is an illegal value. Using default value." + newLine);
					configuration.remove(keyToCheck);
					configuration.put(keyToCheck, Integer.toString(defaultValue));
				}
			} catch (Exception e) {
				result.append(valueFromConfiguration + " is not a valid number. Using default value." + newLine);
				configuration.remove(keyToCheck);
				configuration.put(keyToCheck, Integer.toString(defaultValue));
			}
		}

		return result.toString();
	}

	private static HashMap<String, String> readServerConfiguration(String configFile) throws IOException {

		HashMap<String, String> configurationMap = new HashMap<>();
		String[] lineToKeyAndValue = null;
		BufferedReader configFileBufferReader = null;
		String lineFromFile = null;
		try {
			configFileBufferReader = new BufferedReader(new FileReader(configFile));
			lineFromFile = configFileBufferReader.readLine();
			while (lineFromFile != null) {
				System.out.println(lineFromFile);
				lineToKeyAndValue = lineFromFile.split(configurationSeperator);
				if (lineToKeyAndValue.length == 2) {
					configurationMap.put(lineToKeyAndValue[0], lineToKeyAndValue[1]);
				}

				lineFromFile = configFileBufferReader.readLine();
			}
		} finally {
			if (configFileBufferReader != null) {
				configFileBufferReader.close();
			}
		}

		return configurationMap;
	}
}
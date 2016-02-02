import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Set;

public class Crawler {
	public static final URI SEED_URI = URI.create("http://en.wikipedia.org/wiki/Sustainable_energy");
	public static final int MAX_DEPTH = 5;
	public static final int MAX_URLS_NUM = 1000;

	private static final int POLITENESS_DELAY = 1;
	private static final String BASE_URL = "http://en.wikipedia.org/";
	private static final String ENGLISH_WIKI_LINK_PREFIX = "http://en.wikipedia.org/wiki";

	// set savePageToDisk to true to download matched pages to disk
	public void crawl(URI seedURI, boolean savePageToDisk) throws IOException, InterruptedException {
		Document seedPage = getPage(seedURI);
		LinkedList<URI> frontier = new LinkedList<URI>(getValidUniqueUris(seedPage));

		// visitedURIs and matchedURIs are the same for generic Crawler class; but subclasses can override isMatchedPage
		// to be more selective in saving pages to disk (Example: only save page that has contents match some certain keywords)
		Set<URI> visitedURIs = new HashSet<URI>();
		Set<URI> matchedURIs = new LinkedHashSet<URI>();

		if (isMatchedPage(seedPage, seedURI)) {
			if (savePageToDisk) savePage(seedPage, seedURI);
			matchedURIs.add(seedURI);
		}

		visitedURIs.add(seedURI);

		int depth = 1;
		int timeToIncreaseDepth = frontier.size();
		int nextTimeToIncreaseDepth = 0;

		System.out.println("DEPTH: " + depth);

		while (!frontier.isEmpty() && matchedURIs.size() < MAX_URLS_NUM && depth < MAX_DEPTH) {
			URI currentUri = frontier.removeFirst();

			if (visitedURIs.contains(currentUri)) {
				continue;
			}

			Document currentPage = getPage(currentUri);

			// check if this page match our interest
			if (isMatchedPage(currentPage, currentUri)) {
				if (savePageToDisk) savePage(currentPage, currentUri);
				matchedURIs.add(currentUri);
			}

			// find more URIs to crawl
			Set<URI> pageURIs = getValidUniqueUris(currentPage);
			frontier.addAll(pageURIs);
			nextTimeToIncreaseDepth += pageURIs.size();

			// bookkeeping
			visitedURIs.add(currentUri);
			if (visitedURIs.size() % 100 == 0) {
				System.out.println("Num page visited: " + visitedURIs.size());
			}

			// codes to keep track of current depth
			timeToIncreaseDepth--;
			if (timeToIncreaseDepth == 0) {
				depth++;
				timeToIncreaseDepth = nextTimeToIncreaseDepth;
				nextTimeToIncreaseDepth = 0;
				System.out.println("DEPTH: " + depth);
			}

			// respect the politeness policy
			Thread.sleep(1000);
		}
		saveURIsToFile(matchedURIs);
	}

	public boolean isMatchedPage(Document page, URI uri) {
		return true;
	}

	public Elements getPageAnchorElements(Document page) {
		return page.select("a[href]");
	}

	public Set<URI> getValidUniqueUris(Document page) throws IOException {
		Elements links = getPageAnchorElements(page);

		Set<URI> uris = new HashSet<URI>();
		for (Element link : links) {
			URI uri = null;
			try {
				uri = new URI(link.attr("abs:href"));
				if (isNonEnglishWikiLink(uri) || isFragmentLink(uri) || isAdministrativeLink(uri)) {
					continue;
				}
				uris.add(uri);
			} catch (URISyntaxException e) {
				System.out.println("Bad url: " + link.attr("abs:href"));
				continue;
			}
		}
		return uris;
	}

	public Document getPage(URI uri) throws IOException {
		HttpGet request = new HttpGet(uri);
		HttpClient client = HttpClientBuilder.create().build();

		HttpResponse response = client.execute(request);
		return Jsoup.parse(response.getEntity().getContent(), null, BASE_URL);
	}

	public void saveURIsToFile(Set<URI> uris) {
		StringBuilder stringBuilder = new StringBuilder();
		for(URI uri: uris) {
			stringBuilder.append(uri.toString());
			stringBuilder.append("\n");
		}

		saveStringToFile(stringBuilder.toString(), "visited_urls.txt");
	}

	private void savePage(Document page, URI uri) throws FileNotFoundException {
		saveStringToFile(page.html(), "/Users/andang/IdeaProjects/web-crawler/downloaded/" + uri.toString().replace(ENGLISH_WIKI_LINK_PREFIX, "") + ".html");
	}

	private void saveStringToFile(String str, String filePath) {
		FileOutputStream fop;
		try {
			File file = new File(filePath);
			fop = new FileOutputStream(file);
			file.createNewFile();

			// get the content in bytes
			byte[] contentInBytes = str.getBytes();

			fop.write(contentInBytes);
			fop.flush();
			fop.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private String trim(String s, int width) {
		if (s.length() > width)
			return s.substring(0, width-1) + ".";
		else
			return s;
	}

	private boolean isFragmentLink(URI url) {
		return url.toString().contains("#");
	}

	private boolean isAdministrativeLink(URI url) {
		return url.getPath().contains(":");
	}

	private boolean isNonEnglishWikiLink(URI url) {
		return !url.toString().contains(ENGLISH_WIKI_LINK_PREFIX);
	}

	public static void main(String [] args) {
		Crawler crawler = new Crawler();
		try {
			crawler.crawl(SEED_URI, true);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}

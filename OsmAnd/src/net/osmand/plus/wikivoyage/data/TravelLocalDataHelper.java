package net.osmand.plus.wikivoyage.data;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.IndexConstants;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.api.SQLiteAPI.SQLiteConnection;
import net.osmand.plus.api.SQLiteAPI.SQLiteCursor;
import net.osmand.util.Algorithms;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class TravelLocalDataHelper {

	private static final int HISTORY_ITEMS_LIMIT = 300;

	private final WikivoyageLocalDataDbHelper dbHelper;

	private Map<String, WikivoyageSearchHistoryItem> historyMap = new HashMap<>();
	private List<TravelArticle> savedArticles = new ArrayList<>();

	private final Set<Listener> listeners = new HashSet<>();

	public void addListener(Listener listener) {
		listeners.add(listener);
	}

	public void removeListener(Listener listener) {
		listeners.remove(listener);
	}

	TravelLocalDataHelper(OsmandApplication app) {
		dbHelper = new WikivoyageLocalDataDbHelper(app);
	}

	void refreshCachedData() {
		historyMap = dbHelper.getAllHistoryMap();
		savedArticles = dbHelper.readSavedArticles();
	}

	public List<WikivoyageSearchHistoryItem> getAllHistory() {
		List<WikivoyageSearchHistoryItem> res = new ArrayList<>(historyMap.values());
		Collections.sort(res, new Comparator<WikivoyageSearchHistoryItem>() {
			@Override
			public int compare(WikivoyageSearchHistoryItem item1, WikivoyageSearchHistoryItem item2) {
				if (item1.lastAccessed > item2.lastAccessed) {
					return -1;
				} else if (item1.lastAccessed == item2.lastAccessed) {
					return 0;
				}
				return 1;
			}
		});
		return res;
	}

	public void clearHistory() {
		historyMap.clear();
		dbHelper.clearAllHistory();
	}

	public void addToHistory(@NonNull TravelArticle article) {
		File file = article.getFile();
		String title = article.getTitle();
		String lang = article.getLang();
		String isPartOf = article.getIsPartOf();

		WikivoyageSearchHistoryItem item = new WikivoyageSearchHistoryItem();
		item.articleFile = file;
		item.articleTitle = title;
		item.lang = lang;
		item.isPartOf = isPartOf;
		item.lastAccessed = System.currentTimeMillis();

		String key = item.getKey();
		boolean exists = historyMap.containsKey(key);
		if (!exists) {
			dbHelper.addHistoryItem(item);
			historyMap.put(key, item);
		} else {
			dbHelper.updateHistoryItem(item);
		}
		if (historyMap.size() > HISTORY_ITEMS_LIMIT) {
			List<WikivoyageSearchHistoryItem> allHistory = getAllHistory();
			WikivoyageSearchHistoryItem lastItem = allHistory.get(allHistory.size() - 1);
			dbHelper.removeHistoryItem(lastItem);
			historyMap.remove(key);
		}
	}

	@NonNull
	public List<TravelArticle> getSavedArticles() {
		return new ArrayList<>(savedArticles);
	}

	public void addArticleToSaved(@NonNull TravelArticle article) {
		if (!isArticleSaved(article)) {
			savedArticles.add(article);
			dbHelper.addSavedArticle(article);
			notifySavedUpdated();
		}
	}

	public void restoreSavedArticle(@NonNull TravelArticle article) {
		if (!isArticleSaved(article)) {
			savedArticles.add(article);
			dbHelper.addSavedArticle(article);
			notifySavedUpdated();
		}
	}

	public void removeArticleFromSaved(@NonNull TravelArticle article) {
		TravelArticle savedArticle = getArticle(article.title, article.lang);
		if (savedArticle != null) {
			savedArticles.remove(savedArticle);
			dbHelper.removeSavedArticle(savedArticle);
			notifySavedUpdated();
		}
	}

	public boolean isArticleSaved(@NonNull TravelArticle article) {
		return getArticle(article.title, article.lang) != null;
	}

	private void notifySavedUpdated() {
		for (Listener listener : listeners) {
			listener.savedArticlesUpdated();
		}
	}

	@Nullable
	private TravelArticle getArticle(String title, String lang) {
		for (TravelArticle article : savedArticles) {
			if (article.title != null && article.title.equals(title) && article.lang != null && article.lang.equals(lang)) {
				return article;
			}
		}
		return null;
	}

	@Nullable
	public TravelArticle getSavedArticle(File file, String routeId, String lang) {
		for (TravelArticle article : savedArticles) {
			if (Algorithms.objectEquals(article.file, file)
					&& Algorithms.stringsEqual(article.routeId, routeId)
					&& Algorithms.stringsEqual(article.lang, lang)) {
				return article;
			}
		}
		return null;
	}

	@NonNull
	public List<TravelArticle> getSavedArticles(File file, String routeId) {
		List<TravelArticle> articles = new ArrayList<>();
		for (TravelArticle article : savedArticles) {
			if (Algorithms.objectEquals(article.file, file)
					&& Algorithms.stringsEqual(article.routeId, routeId)) {
				articles.add(article);
			}
		}
		return articles;
	}

	public interface Listener {

		void savedArticlesUpdated();
	}

	private static class WikivoyageLocalDataDbHelper {

		private static final int DB_VERSION = 7;
		private static final String DB_NAME = "wikivoyage_local_data";

		private static final String HISTORY_TABLE_NAME = "wikivoyage_search_history";
		private static final String HISTORY_COL_ARTICLE_TITLE = "article_title";
		private static final String HISTORY_COL_LANG = "lang";
		private static final String HISTORY_COL_IS_PART_OF = "is_part_of";
		private static final String HISTORY_COL_LAST_ACCESSED = "last_accessed";
		private static final String HISTORY_COL_TRAVEL_BOOK = "travel_book";

		private static final String HISTORY_TABLE_CREATE = "CREATE TABLE IF NOT EXISTS " +
				HISTORY_TABLE_NAME + " (" +
				HISTORY_COL_ARTICLE_TITLE + " TEXT, " +
				HISTORY_COL_LANG + " TEXT, " +
				HISTORY_COL_IS_PART_OF + " TEXT, " +
				HISTORY_COL_LAST_ACCESSED + " long, " +
				HISTORY_COL_TRAVEL_BOOK + " TEXT);";

		private static final String HISTORY_TABLE_SELECT = "SELECT " +
				HISTORY_COL_ARTICLE_TITLE + ", " +
				HISTORY_COL_LANG + ", " +
				HISTORY_COL_IS_PART_OF + ", " +
				HISTORY_COL_LAST_ACCESSED +
				" FROM " + HISTORY_TABLE_NAME;

		private static final String BOOKMARKS_TABLE_NAME = "wikivoyage_saved_articles";
		private static final String BOOKMARKS_COL_ARTICLE_TITLE = "article_title";
		private static final String BOOKMARKS_COL_LANG = "lang";
		private static final String BOOKMARKS_COL_IS_PART_OF = "is_part_of";
		private static final String BOOKMARKS_COL_IMAGE_TITLE = "image_title";
		private static final String BOOKMARKS_COL_PARTIAL_CONTENT = "partial_content";
		private static final String BOOKMARKS_COL_TRAVEL_BOOK = "travel_book";
		private static final String BOOKMARKS_COL_LAT = "lat";
		private static final String BOOKMARKS_COL_LON = "lon";
		private static final String BOOKMARKS_COL_ROUTE_ID = "route_id";
		private static final String BOOKMARKS_COL_CONTENT_JSON = "content_json";
		private static final String BOOKMARKS_COL_CONTENT = "content";
		private static final String BOOKMARKS_COL_LAST_MODIFIED = "last_modified";

		private static final String BOOKMARKS_TABLE_CREATE = "CREATE TABLE IF NOT EXISTS " +
				BOOKMARKS_TABLE_NAME + " (" +
				BOOKMARKS_COL_ARTICLE_TITLE + " TEXT, " +
				BOOKMARKS_COL_LANG + " TEXT, " +
				BOOKMARKS_COL_IS_PART_OF + " TEXT, " +
				BOOKMARKS_COL_IMAGE_TITLE + " TEXT, " +
				BOOKMARKS_COL_TRAVEL_BOOK + " TEXT, " +
				BOOKMARKS_COL_LAT + " double, " +
				BOOKMARKS_COL_LON + " double, " +
				BOOKMARKS_COL_ROUTE_ID + " TEXT, " +
				BOOKMARKS_COL_CONTENT_JSON + " TEXT, " +
				BOOKMARKS_COL_CONTENT + " TEXT, " +
				BOOKMARKS_COL_LAST_MODIFIED + " long" + ");";

		private static final String BOOKMARKS_TABLE_SELECT = "SELECT " +
				BOOKMARKS_COL_ARTICLE_TITLE + ", " +
				BOOKMARKS_COL_LANG + ", " +
				BOOKMARKS_COL_IS_PART_OF + ", " +
				BOOKMARKS_COL_IMAGE_TITLE + ", " +
				BOOKMARKS_COL_TRAVEL_BOOK + ", " +
				BOOKMARKS_COL_LAT + ", " +
				BOOKMARKS_COL_LON + ", " +
				BOOKMARKS_COL_ROUTE_ID + ", " +
				BOOKMARKS_COL_CONTENT_JSON + ", " +
				BOOKMARKS_COL_CONTENT + ", " +
				BOOKMARKS_COL_LAST_MODIFIED +
				" FROM " + BOOKMARKS_TABLE_NAME;

		private final OsmandApplication context;

		WikivoyageLocalDataDbHelper(OsmandApplication context) {
			this.context = context;
		}

		@Nullable
		private SQLiteConnection openConnection(boolean readonly) {
			SQLiteConnection conn = context.getSQLiteAPI().getOrCreateDatabase(DB_NAME, readonly);
			if (conn == null) {
				return null;
			}
			if (conn.getVersion() < DB_VERSION) {
				if (readonly) {
					conn.close();
					conn = context.getSQLiteAPI().getOrCreateDatabase(DB_NAME, false);
				}
				int version = conn.getVersion();
				conn.setVersion(DB_VERSION);
				if (version == 0) {
					onCreate(conn);
				} else {
					onUpgrade(conn, version, DB_VERSION);
				}
			}
			return conn;
		}

		private void onCreate(SQLiteConnection conn) {
			conn.execSQL(HISTORY_TABLE_CREATE);
			conn.execSQL(BOOKMARKS_TABLE_CREATE);
		}

		private void onUpgrade(SQLiteConnection conn, int oldVersion, int newVersion) {
			if (oldVersion < 2) {
				conn.execSQL(BOOKMARKS_TABLE_CREATE);
			}
			if (oldVersion < 3) {
				conn.execSQL("ALTER TABLE " + HISTORY_TABLE_NAME + " ADD " + HISTORY_COL_TRAVEL_BOOK + " TEXT");
				conn.execSQL("ALTER TABLE " + BOOKMARKS_TABLE_NAME + " ADD " + BOOKMARKS_COL_TRAVEL_BOOK + " TEXT");
				String selectedTravelBookName = context.getTravelHelper().getSelectedTravelBookName();
				if (selectedTravelBookName != null) {
					Object[] args = new Object[]{selectedTravelBookName};
					conn.execSQL("UPDATE " + HISTORY_TABLE_NAME + " SET " + HISTORY_COL_TRAVEL_BOOK + " = ?", args);
					conn.execSQL("UPDATE " + BOOKMARKS_TABLE_NAME + " SET " + BOOKMARKS_COL_TRAVEL_BOOK + " = ?", args);
				}
			}
			if (oldVersion < 4) {
				conn.execSQL("ALTER TABLE " + BOOKMARKS_TABLE_NAME + " ADD " + BOOKMARKS_COL_LAT + " double");
				conn.execSQL("ALTER TABLE " + BOOKMARKS_TABLE_NAME + " ADD " + BOOKMARKS_COL_LON + " double");
			}
			if (oldVersion < 5) {
				conn.execSQL("ALTER TABLE " + BOOKMARKS_TABLE_NAME + " ADD " + BOOKMARKS_COL_ROUTE_ID + " TEXT");
			}
			if (oldVersion < 6) {
				conn.execSQL("ALTER TABLE " + BOOKMARKS_TABLE_NAME + " ADD " + BOOKMARKS_COL_CONTENT_JSON + " TEXT");
				conn.execSQL("ALTER TABLE " + BOOKMARKS_TABLE_NAME + " ADD " + BOOKMARKS_COL_CONTENT + " TEXT");
			}
			if (oldVersion < 7) {
				conn.execSQL("ALTER TABLE " + BOOKMARKS_TABLE_NAME + " ADD " + BOOKMARKS_COL_LAST_MODIFIED + " long");
				conn.execSQL("UPDATE " + BOOKMARKS_TABLE_NAME +
						" SET " + BOOKMARKS_COL_CONTENT + " = " + BOOKMARKS_COL_PARTIAL_CONTENT +
						" WHERE " + BOOKMARKS_COL_CONTENT + " is null");
				conn.execSQL("UPDATE " + BOOKMARKS_TABLE_NAME +
						" SET " + BOOKMARKS_COL_PARTIAL_CONTENT + " = null");
			}
		}

		@NonNull
		Map<String, WikivoyageSearchHistoryItem> getAllHistoryMap() {
			Map<String, WikivoyageSearchHistoryItem> res = new LinkedHashMap<>();
			SQLiteConnection conn = openConnection(true);
			if (conn != null) {
				try {
					SQLiteCursor cursor = conn.rawQuery(HISTORY_TABLE_SELECT, null);
					if (cursor != null) {
						if (cursor.moveToFirst()) {
							do {
								WikivoyageSearchHistoryItem item = readHistoryItem(cursor);
								res.put(item.getKey(), item);
							} while (cursor.moveToNext());
						}
						cursor.close();
					}
				} finally {
					conn.close();
				}
			}
			return res;
		}

		void addHistoryItem(@NonNull WikivoyageSearchHistoryItem item) {
			String travelBook = item.getTravelBook(context);
			if (travelBook == null) {
				return;
			}
			SQLiteConnection conn = openConnection(false);
			if (conn != null) {
				try {
					conn.execSQL("INSERT INTO " + HISTORY_TABLE_NAME + "(" + HISTORY_COL_ARTICLE_TITLE + ", "
							+ HISTORY_COL_LANG + ", " + HISTORY_COL_IS_PART_OF + ", " + HISTORY_COL_LAST_ACCESSED
							+ ", " + HISTORY_COL_TRAVEL_BOOK + ") VALUES (?, ?, ?, ?, ?)", new Object[] {
							item.articleTitle, item.lang, item.isPartOf, item.lastAccessed, travelBook });
				} finally {
					conn.close();
				}
			}
		}

		void updateHistoryItem(@NonNull WikivoyageSearchHistoryItem item) {
			String travelBook = item.getTravelBook(context);
			if (travelBook == null) {
				return;
			}
			SQLiteConnection conn = openConnection(false);
			if (conn != null) {
				try {
					conn.execSQL("UPDATE " + HISTORY_TABLE_NAME + " SET " +
									HISTORY_COL_IS_PART_OF + " = ?, " +
									HISTORY_COL_LAST_ACCESSED + " = ? " +
									"WHERE " + HISTORY_COL_ARTICLE_TITLE + " = ? " +
									" AND " + HISTORY_COL_LANG + " = ?" +
									" AND " + HISTORY_COL_TRAVEL_BOOK + " = ?",
							new Object[]{item.isPartOf, item.lastAccessed, 
								item.articleTitle, item.lang, travelBook});
				} finally {
					conn.close();
				}
			}
		}

		void removeHistoryItem(@NonNull WikivoyageSearchHistoryItem item) {
			String travelBook = item.getTravelBook(context);
			if (travelBook == null) {
				return;
			}
			SQLiteConnection conn = openConnection(false);
			if (conn != null) {
				try {
					conn.execSQL("DELETE FROM " + HISTORY_TABLE_NAME +
									" WHERE " + HISTORY_COL_ARTICLE_TITLE+ " = ?" +
									" AND " + HISTORY_COL_LANG + " = ?" +
									" AND " + HISTORY_COL_TRAVEL_BOOK + " = ?",
							new Object[]{item.articleTitle, item.lang, travelBook});
				} finally {
					conn.close();
				}
			}
		}

		void clearAllHistory() {
			SQLiteConnection conn = openConnection(false);
			if (conn != null) {
				try {
					conn.execSQL("DELETE FROM " + HISTORY_TABLE_NAME);
				} finally {
					conn.close();
				}
			}
		}

		@NonNull
		List<TravelArticle> readSavedArticles() {
			List<TravelArticle> res = new ArrayList<>();
			SQLiteConnection conn = openConnection(true);
			if (conn != null) {
				try {
					SQLiteCursor cursor = conn.rawQuery(BOOKMARKS_TABLE_SELECT, null);
					if (cursor != null) {
						if (cursor.moveToFirst()) {
							do {
								TravelArticle dbArticle = readSavedArticle(cursor);
								TravelArticle article = context.getTravelHelper().getArticleById(dbArticle.generateIdentifier(), dbArticle.lang);
								if (article != null && article.getLastModified() > dbArticle.getLastModified()) {
									updateSavedArticle(dbArticle, article);
									res.add(article);
								} else {
									res.add(dbArticle);
								}
							} while (cursor.moveToNext());
						}
						cursor.close();
					}
				} finally {
					conn.close();
				}
			}
			return res;
		}

		void addSavedArticle(@NonNull TravelArticle article) {
			String travelBook = article.getTravelBook(context);
			if (travelBook == null) {
				return;
			}
			SQLiteConnection conn = openConnection(false);
			if (conn != null) {
				try {
					String query = "INSERT INTO " + BOOKMARKS_TABLE_NAME + " (" +
							BOOKMARKS_COL_ARTICLE_TITLE + ", " +
							BOOKMARKS_COL_LANG + ", " +
							BOOKMARKS_COL_IS_PART_OF + ", " +
							BOOKMARKS_COL_IMAGE_TITLE + ", " +
							BOOKMARKS_COL_TRAVEL_BOOK + ", " +
							BOOKMARKS_COL_LAT + ", " +
							BOOKMARKS_COL_LON + ", " +
							BOOKMARKS_COL_ROUTE_ID + ", " +
							BOOKMARKS_COL_CONTENT_JSON + ", " +
							BOOKMARKS_COL_CONTENT + ", " +
							BOOKMARKS_COL_LAST_MODIFIED +
							") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
					conn.execSQL(query, new Object[]{article.title, article.lang,
							article.aggregatedPartOf, article.imageTitle,
							travelBook, article.lat, article.lon, article.routeId, article.contentsJson,
							article.content, article.getFile().lastModified()});
				} finally {
					conn.close();
				}
			}
		}

		void removeSavedArticle(@NonNull TravelArticle article) {
			String travelBook = article.getTravelBook(context);
			if (travelBook == null) {
				return;
			}
			SQLiteConnection conn = openConnection(false);
			if (conn != null) {
				try {
					conn.execSQL("DELETE FROM " + BOOKMARKS_TABLE_NAME +
									" WHERE " + BOOKMARKS_COL_ARTICLE_TITLE + " = ?" +
									" AND " + BOOKMARKS_COL_ROUTE_ID + " = ?" +
									" AND " + BOOKMARKS_COL_LANG + " = ?" +
									" AND " + BOOKMARKS_COL_TRAVEL_BOOK + " = ?",
							new Object[]{article.title, article.routeId, article.lang, travelBook});
				} finally {
					conn.close();
				}
			}
		}

		void updateSavedArticle(@NonNull TravelArticle odlArticle, @NonNull TravelArticle newArticle) {
			String travelBook = odlArticle.getTravelBook(context);
			if (travelBook == null) {
				return;
			}
			SQLiteConnection conn = openConnection(false);
			if (conn != null) {
				try {
					conn.execSQL("UPDATE " + BOOKMARKS_TABLE_NAME + " SET " +
									BOOKMARKS_COL_ARTICLE_TITLE + " = ?, " +
									BOOKMARKS_COL_LANG + " = ?, " +
									BOOKMARKS_COL_IS_PART_OF + " = ?, " +
									BOOKMARKS_COL_IMAGE_TITLE + " = ?, " +
									BOOKMARKS_COL_TRAVEL_BOOK + " = ?, " +
									BOOKMARKS_COL_LAT + " = ?, " +
									BOOKMARKS_COL_LON + " = ?, " +
									BOOKMARKS_COL_ROUTE_ID + " = ?, " +
									BOOKMARKS_COL_CONTENT_JSON + " = ?, " +
									BOOKMARKS_COL_CONTENT + " = ?, " +
									BOOKMARKS_COL_LAST_MODIFIED + " = ? " +
									"WHERE " + BOOKMARKS_COL_ARTICLE_TITLE + " = ? " +
									" AND " + BOOKMARKS_COL_ROUTE_ID + " = ?" +
									" AND " + BOOKMARKS_COL_LANG + " = ?" +
									" AND " + BOOKMARKS_COL_TRAVEL_BOOK + " = ?",
							new Object[]{newArticle.title, newArticle.lang, newArticle.aggregatedPartOf,
									newArticle.imageTitle, travelBook, newArticle.lat, newArticle.lon,
									newArticle.routeId, newArticle.content, newArticle.contentsJson,
									odlArticle.title, odlArticle.routeId, odlArticle.lang, travelBook});

				} finally {
					conn.close();
				}
			}
		}

		@NonNull
		private WikivoyageSearchHistoryItem readHistoryItem(SQLiteCursor cursor) {
			WikivoyageSearchHistoryItem res = new WikivoyageSearchHistoryItem();
			res.articleTitle = cursor.getString(cursor.getColumnIndex(HISTORY_COL_ARTICLE_TITLE));
			res.lang = cursor.getString(cursor.getColumnIndex(HISTORY_COL_LANG));
			res.isPartOf = cursor.getString(cursor.getColumnIndex(HISTORY_COL_IS_PART_OF));
			res.lastAccessed = cursor.getLong(cursor.getColumnIndex(HISTORY_COL_LAST_ACCESSED));
			return res;
		}

		@NonNull
		private TravelArticle readSavedArticle(SQLiteCursor cursor) {
			TravelArticle res = new TravelArticle();
			res.title = cursor.getString(cursor.getColumnIndex(BOOKMARKS_COL_ARTICLE_TITLE));
			res.lang = cursor.getString(cursor.getColumnIndex(BOOKMARKS_COL_LANG));
			res.aggregatedPartOf = cursor.getString(cursor.getColumnIndex(BOOKMARKS_COL_IS_PART_OF));
			res.imageTitle = cursor.getString(cursor.getColumnIndex(BOOKMARKS_COL_IMAGE_TITLE));
			res.content = cursor.getString(cursor.getColumnIndex(BOOKMARKS_COL_CONTENT));
			res.lat = cursor.getDouble(cursor.getColumnIndex(BOOKMARKS_COL_LAT));
			res.lon = cursor.getDouble(cursor.getColumnIndex(BOOKMARKS_COL_LON));
			res.routeId = cursor.getString(cursor.getColumnIndex(BOOKMARKS_COL_ROUTE_ID));
			res.contentsJson = cursor.getString(cursor.getColumnIndex(BOOKMARKS_COL_CONTENT_JSON));
			String travelBook = cursor.getString(cursor.getColumnIndex(BOOKMARKS_COL_TRAVEL_BOOK));
			if (!Algorithms.isEmpty(travelBook)) {
				res.file = context.getAppPath(IndexConstants.WIKIVOYAGE_INDEX_DIR + travelBook);
				res.lastModified = cursor.getLong(cursor.getColumnIndex(BOOKMARKS_COL_LAST_MODIFIED));
			}
			return res;
		}
	}
}

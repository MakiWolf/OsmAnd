package net.osmand.plus.myplaces;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.ListPopupWindow;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

import net.osmand.AndroidUtils;
import net.osmand.GPXUtilities;
import net.osmand.GPXUtilities.GPXFile;
import net.osmand.GPXUtilities.WptPt;
import net.osmand.PicassoUtils;
import net.osmand.data.PointDescription;
import net.osmand.data.QuadRect;
import net.osmand.plus.GPXDatabase.GpxDataItem;
import net.osmand.plus.GpxSelectionHelper;
import net.osmand.plus.GpxSelectionHelper.GpxDisplayGroup;
import net.osmand.plus.GpxSelectionHelper.GpxDisplayItemType;
import net.osmand.plus.GpxSelectionHelper.SelectedGpxFile;
import net.osmand.plus.OsmAndFormatter;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.activities.TrackActivity;
import net.osmand.plus.dialogs.GpxAppearanceAdapter;
import net.osmand.plus.myplaces.TrackBitmapDrawer.TrackBitmapDrawerListener;
import net.osmand.plus.settings.backend.CommonPreference;
import net.osmand.plus.settings.backend.OsmandSettings;
import net.osmand.plus.track.GpxSplitType;
import net.osmand.plus.track.SplitTrackAsyncTask;
import net.osmand.plus.track.SplitTrackAsyncTask.SplitTrackListener;
import net.osmand.plus.widgets.tools.CropCircleTransformation;
import net.osmand.plus.wikipedia.WikiArticleHelper;
import net.osmand.plus.wikivoyage.WikivoyageUtils;
import net.osmand.plus.wikivoyage.article.WikivoyageArticleDialogFragment;
import net.osmand.plus.wikivoyage.data.TravelArticle;
import net.osmand.render.RenderingRulesStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import gnu.trove.list.array.TIntArrayList;

import static net.osmand.plus.dialogs.ConfigureMapMenu.CURRENT_TRACK_COLOR_ATTR;

public class TrackActivityFragmentAdapter implements TrackBitmapDrawerListener {

	private OsmandApplication app;
	private Fragment fragment;
	private ListView listView;
	private GpxDisplayItemType[] filterTypes;

	private List<String> options = new ArrayList<>();
	private List<Double> distanceSplit = new ArrayList<>();
	private TIntArrayList timeSplit = new TIntArrayList();
	private int selectedSplitInterval;
	private boolean updateEnable;
	private View headerView;
	private SwitchCompat vis;

	private boolean showMapOnly;

	private boolean trackBitmapSelectionSupported;
	private ImageView imageView;
	private ProgressBar progressBar;

	private boolean showDescriptionCard;

	private boolean fabMenuOpened = false;
	private FloatingActionButton menuFab;
	private FloatingActionButton waypointFab;
	private View waypointTextLayout;
	private FloatingActionButton routePointFab;
	private View routePointTextLayout;
	private FloatingActionButton lineFab;
	private View lineTextLayout;
	private View overlayView;

	ListPopupWindow splitListPopupWindow;
	ListPopupWindow colorListPopupWindow;

	TrackActivityFragmentAdapter(@NonNull OsmandApplication app,
								 @NonNull Fragment fragment,
								 @NonNull ListView listView,
								 @NonNull GpxDisplayItemType... filterTypes) {
		this.app = app;
		this.fragment = fragment;
		this.listView = listView;
		this.filterTypes = filterTypes;
	}

	public void onActivityCreated(Bundle savedInstanceState) {
		listView.setOnScrollListener(new AbsListView.OnScrollListener() {
			@Override
			public void onScrollStateChanged(AbsListView absListView, int i) {
				if (i == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
					if (fabMenuOpened) {
						hideTransparentOverlay();
						closeFabMenu(absListView.getContext());
					}
				}
			}

			@Override
			public void onScroll(AbsListView absListView, int i, int i1, int i2) {
			}
		});
		listView.setBackgroundColor(ContextCompat.getColor(app, app.getSettings().isLightContent()
				? R.color.activity_background_color_light : R.color.activity_background_color_dark));
	}

	public void onCreateView(@NonNull View view) {
		fragment.setHasOptionsMenu(true);
		Context context = view.getContext();

		ListView listView = (ListView) view.findViewById(android.R.id.list);
		listView.setDivider(null);
		listView.setDividerHeight(0);

		overlayView = view.findViewById(R.id.overlay_view);
		overlayView.setOnClickListener(onFabClickListener);

		menuFab = (FloatingActionButton) view.findViewById(R.id.menu_fab);
		menuFab.setOnClickListener(onFabClickListener);

		waypointFab = (FloatingActionButton) view.findViewById(R.id.waypoint_fab);
		waypointFab.setOnClickListener(onFabClickListener);
		waypointTextLayout = view.findViewById(R.id.waypoint_text_layout);
		waypointTextLayout.setOnClickListener(onFabClickListener);

		routePointFab = (FloatingActionButton) view.findViewById(R.id.route_fab);
		routePointFab.setOnClickListener(onFabClickListener);
		routePointTextLayout = view.findViewById(R.id.route_text_layout);
		routePointTextLayout.setOnClickListener(onFabClickListener);

		lineFab = (FloatingActionButton) view.findViewById(R.id.line_fab);
		lineFab.setOnClickListener(onFabClickListener);
		lineTextLayout = view.findViewById(R.id.line_text_layout);
		lineTextLayout.setOnClickListener(onFabClickListener);

		TextView tv = new TextView(context);
		tv.setText(R.string.none_selected_gpx);
		tv.setTextSize(24);
		listView.setEmptyView(tv);

		LayoutInflater inflater = LayoutInflater.from(context);
		headerView = inflater.inflate(R.layout.gpx_item_list_header, null, false);
		listView.addHeaderView(headerView);
		if (showDescriptionCard) {
			View card = getDescriptionCardView(context);
			if (card != null) {
				listView.addHeaderView(card, null, false);
			}
		}
		listView.addFooterView(inflater.inflate(R.layout.list_shadow_footer, null, false));
		View emptyView = new View(context);
		emptyView.setLayoutParams(new AbsListView.LayoutParams(
				AbsListView.LayoutParams.MATCH_PARENT,
				AndroidUtils.dpToPx(context, 72)));
		listView.addFooterView(emptyView);

		updateHeader(0);
	}

	@Nullable
	public TrackActivity getTrackActivity() {
		return (TrackActivity) fragment.getActivity();
	}

	@Nullable
	private TrackBitmapDrawer getTrackBitmapDrawer() {
		TrackActivity activity = getTrackActivity();
		return activity != null ? activity.getTrackBitmapDrawer() : null;
	}

	private GPXFile getGpx() {
		TrackActivity activity = getTrackActivity();
		return activity != null ? activity.getGpx() : null;
	}

	@Nullable
	private GpxDataItem getGpxDataItem() {
		TrackActivity activity = getTrackActivity();
		return activity != null ? activity.getGpxDataItem() : null;
	}

	private void showTrackBitmapProgress() {
		if (progressBar != null) {
			progressBar.setVisibility(View.VISIBLE);
		}
	}

	private void hideTrackBitmapProgress() {
		if (progressBar != null) {
			progressBar.setVisibility(View.GONE);
		}
	}

	public boolean isUpdateEnable() {
		return updateEnable;
	}

	public void setUpdateEnable(boolean updateEnable) {
		this.updateEnable = updateEnable;
		TrackBitmapDrawer trackDrawer = getTrackBitmapDrawer();
		if (trackDrawer != null) {
			getTrackBitmapDrawer().setDrawEnabled(updateEnable);
		}
	}

	public void setShowMapOnly(boolean showMapOnly) {
		this.showMapOnly = showMapOnly;
	}

	public void setTrackBitmapSelectionSupported(boolean trackBitmapSelectionSupported) {
		this.trackBitmapSelectionSupported = trackBitmapSelectionSupported;
	}

	public void setShowDescriptionCard(boolean showDescriptionCard) {
		this.showDescriptionCard = showDescriptionCard;
	}

	private void refreshTrackBitmap() {
		TrackBitmapDrawer trackDrawer = getTrackBitmapDrawer();
		if (trackDrawer != null) {
			trackDrawer.refreshTrackBitmap();
		}
	}

	public void updateHeader(int listItemsCount) {
		progressBar = (ProgressBar) headerView.findViewById(R.id.mapLoadProgress);
		imageView = (ImageView) headerView.findViewById(R.id.imageView);
		imageView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				showTemporaryObjectOnMap(getRect());
			}
		});
		final View splitColorView = headerView.findViewById(R.id.split_color_view);
		final View appearanceView = headerView.findViewById(R.id.appearance_view);
		final View divider = headerView.findViewById(R.id.divider);
		final View splitIntervalView = headerView.findViewById(R.id.split_interval_view);
		vis = (SwitchCompat) headerView.findViewById(R.id.showOnMapToggle);
		final View bottomDivider = headerView.findViewById(R.id.bottom_divider);
		GPXFile gpxFile = getGpx();
		boolean gpxFileSelected = isGpxFileSelected(gpxFile);

		boolean hasPath = gpxFile != null && (gpxFile.tracks.size() > 0 || gpxFile.routes.size() > 0);
		TrackActivity activity = getTrackActivity();
		TrackBitmapDrawer trackDrawer = getTrackBitmapDrawer();
		if (activity != null && trackDrawer != null) {
			if (trackDrawer.isNonInitialized()) {
				if (trackDrawer.initAndDraw()) {
					imageView.setVisibility(View.VISIBLE);
				} else {
					imageView.setVisibility(View.GONE);
				}
			} else {
				refreshTrackBitmap();
			}
		}

		vis.setChecked(gpxFileSelected);
		headerView.findViewById(R.id.showOnMapContainer).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				vis.toggle();
				if (!vis.isChecked()) {
					selectedSplitInterval = 0;
				}
				setTrackVisibilityOnMap(vis.isChecked());
				if (!showMapOnly) {
					updateSplitIntervalView(splitIntervalView);
				}
				TrackActivity trackActivity = getTrackActivity();
				if (trackActivity != null) {
					trackActivity.updateHeader(fragment);
					trackActivity.invalidateOptionsMenu();
				}
			}
		});

		splitColorView.setVisibility(View.GONE);
		if (showMapOnly) {
			splitIntervalView.setVisibility(View.GONE);
			appearanceView.setVisibility(View.GONE);
			divider.setVisibility(View.GONE);
			bottomDivider.setVisibility(View.VISIBLE);
		} else {
			bottomDivider.setVisibility(View.GONE);

			if (hasPath) {
				if (!gpxFile.showCurrentTrack && listItemsCount > 0) {
					prepareSplitIntervalAdapterData();
					setupSplitIntervalView(splitIntervalView);
					updateSplitIntervalView(splitIntervalView);
					splitIntervalView.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							TrackActivity activity = getTrackActivity();
							if (activity != null) {
								ListAdapter adapter = new ArrayAdapter<>(activity, R.layout.popup_list_text_item, options);
								OnItemClickListener itemClickListener = new OnItemClickListener() {

									@Override
									public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
										selectedSplitInterval = position;
										setTrackVisibilityOnMap(vis.isChecked());
										splitListPopupWindow.dismiss();
										updateSplitIntervalView(splitIntervalView);
									}
								};
								splitListPopupWindow = createPopupWindow(activity, splitIntervalView, adapter, itemClickListener);
								splitListPopupWindow.show();
							}
						}
					});
					splitIntervalView.setVisibility(View.VISIBLE);
				} else {
					splitIntervalView.setVisibility(View.GONE);
				}
				appearanceView.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						showTemporaryObjectOnMap(getGpx());
					}
				});
				appearanceView.setVisibility(View.VISIBLE);
				divider.setVisibility(View.VISIBLE);
			} else {
				appearanceView.setVisibility(View.GONE);
				divider.setVisibility(View.GONE);
			}
		}
		updateTrackColor();
	}

	private void showTemporaryObjectOnMap(Object toShow) {
		TrackActivity activity = getTrackActivity();
		GpxDataItem gpxDataItem = getGpxDataItem();
		GPXFile gpx = getGpx();
		WptPt pointToShow = gpx != null ? gpx.findPointToShow() : null;
		if (activity != null && pointToShow != null) {
			boolean gpxFileSelected = isGpxFileSelected(gpx);
			if (!gpxFileSelected) {
				Intent intent = activity.getIntent();
				if (intent != null) {
					intent.putExtra(TrackActivity.SHOW_TEMPORARILY, true);
				}
			}
			setTrackVisibilityOnMap(true);

			final OsmandSettings settings = app.getSettings();
			String trackName;
			if (gpx.showCurrentTrack) {
				trackName = app.getString(R.string.shared_string_currently_recording_track);
			} else if (gpxDataItem != null) {
				trackName = gpxDataItem.getFile().getName();
			} else {
				trackName = gpx.path;
			}
			settings.setMapLocationToShow(pointToShow.getLatitude(), pointToShow.getLongitude(),
					settings.getLastKnownMapZoom(),
					new PointDescription(PointDescription.POINT_TYPE_WPT, trackName),
					false,
					toShow
			);
			MapActivity.launchMapActivityMoveToTop(activity);
		}
	}

	private ListPopupWindow createPopupWindow(Activity activity, View anchorView, ListAdapter adapter, OnItemClickListener itemClickListener) {
		ListPopupWindow popupWindow = new ListPopupWindow(activity);
		popupWindow.setAnchorView(anchorView);
		popupWindow.setContentWidth(AndroidUtils.dpToPx(app, 200f));
		popupWindow.setModal(true);
		popupWindow.setDropDownGravity(Gravity.RIGHT | Gravity.TOP);
		popupWindow.setVerticalOffset(AndroidUtils.dpToPx(app, -48f));
		popupWindow.setHorizontalOffset(AndroidUtils.dpToPx(app, -6f));
		popupWindow.setAdapter(adapter);
		popupWindow.setOnItemClickListener(itemClickListener);

		return popupWindow;
	}

	@Nullable
	private View getDescriptionCardView(Context context) {
		GPXFile gpx = getGpx();
		if (gpx == null || gpx.metadata == null) {
			return null;
		}

		TravelArticle article = getTravelArticle(gpx.metadata);
		if (article != null) {
			return createTravelArticleCard(context, article);
		}

		final String description = getMetadataDescription(gpx.metadata);

		if (!TextUtils.isEmpty(description)) {
			String link = getMetadataImageLink(gpx.metadata);
			if (!TextUtils.isEmpty(link)) {
				View.OnClickListener onClickListener = new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						openGpxDescriptionDialog(description);
					}
				};
				return createArticleCard(context, null, description, null, link, onClickListener);
			} else {
				return createDescriptionCard(context, description);
			}
		}

		return null;
	}

	@Nullable
	private String getMetadataDescription(@NonNull GPXUtilities.Metadata metadata) {
		String descHtml = metadata.desc;
		if (TextUtils.isEmpty(descHtml)) {
			Map<String, String> extensions = metadata.getExtensionsToRead();
			if (!extensions.isEmpty() && extensions.containsKey("desc")) {
				descHtml = extensions.get("desc");
			}
		}
		if (descHtml != null) {
			String content = WikiArticleHelper.getPartialContent(descHtml);
			if (!TextUtils.isEmpty(content)) {
				return content;
			}
		}
		return descHtml;
	}

	@Nullable
	private String getMetadataImageLink(@NonNull GPXUtilities.Metadata metadata) {
		String link = metadata.link;
		if (!TextUtils.isEmpty(link)) {
			String lowerCaseLink = link.toLowerCase();
			if (lowerCaseLink.contains(".jpg")
					|| lowerCaseLink.contains(".jpeg")
					|| lowerCaseLink.contains(".png")
					|| lowerCaseLink.contains(".bmp")
					|| lowerCaseLink.contains(".webp")) {
				return link;
			}
		}
		return null;
	}

	@Nullable
	private TravelArticle getTravelArticle(@NonNull GPXUtilities.Metadata metadata) {
		String title = metadata.getArticleTitle();
		String lang = metadata.getArticleLang();
		if (!TextUtils.isEmpty(title) && !TextUtils.isEmpty(lang)) {
			return app.getTravelHelper().getArticle(title, lang);
		}
		return null;
	}

	private Drawable getReadIcon() {
		int colorId = app.getSettings().isLightContent() ? R.color.wikivoyage_active_light : R.color.wikivoyage_active_dark;
		return app.getUIUtilities().getIcon(R.drawable.ic_action_read_article, colorId);
	}

	private View createTravelArticleCard(final Context context, @NonNull final TravelArticle article) {
		String title = article.getTitle();
		String content = WikiArticleHelper.getPartialContent(article.getContent());
		String geoDescription = article.getGeoDescription();
		String imageUrl = TravelArticle.getImageUrl(article.getImageTitle(), false);
		View.OnClickListener onClickListener = new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				TrackActivity activity = getTrackActivity();
				if (activity != null) {
					WikivoyageArticleDialogFragment.showInstance(app,
							activity.getSupportFragmentManager(), article.getTripId(), article.getLang());
				}
			}
		};
		return createArticleCard(context, title, content, geoDescription, imageUrl, onClickListener);
	}

	private View createArticleCard(final Context context, String title, String content, String geoDescription, final String imageUrl, View.OnClickListener readBtnClickListener) {
		View card = LayoutInflater.from(context).inflate(R.layout.wikivoyage_article_card, null);
		card.findViewById(R.id.background_view).setBackgroundColor(ContextCompat.getColor(context,
				app.getSettings().isLightContent() ? R.color.list_background_color_light : R.color.list_background_color_dark));

		if (!TextUtils.isEmpty(title)) {
			((TextView) card.findViewById(R.id.title)).setText(title);
		} else {
			card.findViewById(R.id.title).setVisibility(View.GONE);
		}
		((TextView) card.findViewById(R.id.content)).setText(content);
		((TextView) card.findViewById(R.id.part_of)).setText(geoDescription);

		final ImageView icon = (ImageView) card.findViewById(R.id.icon);
		final PicassoUtils picassoUtils = PicassoUtils.getPicasso(app);
		RequestCreator rc = Picasso.get().load(imageUrl);
		WikivoyageUtils.setupNetworkPolicy(app.getSettings(), rc);
		rc.transform(new CropCircleTransformation())
				.into(icon, new Callback() {
					@Override
					public void onSuccess() {
						icon.setVisibility(View.VISIBLE);
						picassoUtils.setResultLoaded(imageUrl, true);
					}

					@Override
					public void onError(Exception e) {
						picassoUtils.setResultLoaded(imageUrl, false);
					}
				});
		TextView readBtn = (TextView) card.findViewById(R.id.left_button);
		readBtn.setText(app.getString(R.string.shared_string_read));
		readBtn.setCompoundDrawablesWithIntrinsicBounds(getReadIcon(), null, null, null);
		readBtn.setOnClickListener(readBtnClickListener);
		card.findViewById(R.id.right_button).setVisibility(View.GONE);
		card.findViewById(R.id.divider).setVisibility(View.GONE);
		card.findViewById(R.id.list_item_divider).setVisibility(View.VISIBLE);
		return card;
	}

	private View createDescriptionCard(final Context context, @NonNull final String descHtml) {
		String desc = Html.fromHtml(descHtml).toString().trim();
		if (!TextUtils.isEmpty(desc)) {
			View card = LayoutInflater.from(context).inflate(R.layout.gpx_description_card, null);
			card.findViewById(R.id.background_view).setBackgroundColor(ContextCompat.getColor(context,
					app.getSettings().isLightContent() ? R.color.list_background_color_light : R.color.list_background_color_dark));
			((TextView) card.findViewById(R.id.description)).setText(desc);
			TextView readBtn = (TextView) card.findViewById(R.id.read_button);
			readBtn.setCompoundDrawablesWithIntrinsicBounds(getReadIcon(), null, null, null);
			readBtn.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					openGpxDescriptionDialog(descHtml);
				}
			});
			return card;
		}
		return null;
	}

	private void openGpxDescriptionDialog(@NonNull final String descHtml) {
		TrackActivity activity = getTrackActivity();
		if (activity != null) {
			Bundle args = new Bundle();
			args.putString(GpxDescriptionDialogFragment.CONTENT_KEY, descHtml);
			GpxDescriptionDialogFragment fragment = new GpxDescriptionDialogFragment();
			fragment.setArguments(args);
			fragment.show(activity.getSupportFragmentManager(), GpxDescriptionDialogFragment.TAG);
		}
	}

	public boolean isGpxFileSelected(GPXFile gpxFile) {
		return gpxFile != null &&
				((gpxFile.showCurrentTrack && app.getSelectedGpxHelper().getSelectedCurrentRecordingTrack() != null) ||
						(gpxFile.path != null && app.getSelectedGpxHelper().getSelectedFileByPath(gpxFile.path) != null));
	}

	private void setTrackVisibilityOnMap(boolean visible) {
		GPXFile gpxFile = getGpx();
		if (gpxFile != null) {
			GpxSelectionHelper gpxHelper = app.getSelectedGpxHelper();
			SelectedGpxFile sf = gpxHelper.selectGpxFile(gpxFile, visible, false);
			if (gpxFile.hasTrkPt()) {
				List<GpxDisplayGroup> groups = getDisplayGroups();
				if (groups.size() > 0) {
					updateSplit(groups, visible ? sf : null);
					if (getGpxDataItem() != null) {
						updateSplitInDatabase();
					}
				}
			}
		}
	}

	@Nullable
	private QuadRect getRect() {
		TrackActivity activity = getTrackActivity();
		return activity != null ? activity.getRect() : null;
	}

	public boolean isShowOnMap() {
		return vis != null && vis.isChecked();
	}

	public void updateSelectedPoint(double lat, double lon) {
		TrackBitmapDrawer trackDrawer = getTrackBitmapDrawer();
		if (trackDrawer != null) {
			trackDrawer.updateSelectedPoint(lat, lon);
		}
	}

	public void updateMenuFabVisibility(boolean visible) {
		menuFab.setVisibility(visible ? View.VISIBLE : View.GONE);
	}

	@NonNull
	public List<GpxDisplayGroup> getOriginalGroups() {
		return filterGroups(false);
	}

	@NonNull
	public List<GpxDisplayGroup> getDisplayGroups() {
		return filterGroups(true);
	}

	private boolean hasFilterType(GpxDisplayItemType filterType) {
		for (GpxDisplayItemType type : filterTypes) {
			if (type == filterType) {
				return true;
			}
		}
		return false;
	}

	@NonNull
	private List<GpxDisplayGroup> filterGroups(boolean useDisplayGroups) {
		List<GpxDisplayGroup> groups = new ArrayList<>();
		TrackActivity activity = getTrackActivity();
		if (activity != null) {
			List<GpxDisplayGroup> result = activity.getGpxFile(useDisplayGroups);
			for (GpxDisplayGroup group : result) {
				boolean add = hasFilterType(group.getType());
				if (add) {
					groups.add(group);
				}

			}
		}
		return groups;
	}

	private void setupSplitIntervalView(View view) {
		final TextView title = (TextView) view.findViewById(R.id.split_interval_title);
		final TextView text = (TextView) view.findViewById(R.id.split_interval_text);
		final ImageView img = (ImageView) view.findViewById(R.id.split_interval_arrow);
		int colorId;
		final List<GpxDisplayGroup> groups = getDisplayGroups();
		if (groups.size() > 0) {
			colorId = app.getSettings().isLightContent() ?
					R.color.text_color_primary_light : R.color.text_color_primary_dark;
		} else {
			colorId = app.getSettings().isLightContent() ?
					R.color.text_color_secondary_light : R.color.text_color_secondary_dark;
		}
		int color = app.getResources().getColor(colorId);
		title.setTextColor(color);
		text.setTextColor(color);
		img.setImageDrawable(app.getUIUtilities().getIcon(R.drawable.ic_action_arrow_drop_down, colorId));
	}

	private void updateSplitIntervalView(View view) {
		final TextView text = (TextView) view.findViewById(R.id.split_interval_text);
		selectedSplitInterval = getSelectedSplitInterval();
		if (selectedSplitInterval == 0) {
			text.setText(app.getString(R.string.shared_string_none));
		} else {
			text.setText(options.get(selectedSplitInterval));
		}
	}

	private int getSelectedSplitInterval() {
		if (getGpxDataItem() == null) {
			return 0;
		}
		int splitType = getGpxDataItem().getSplitType();
		double splitInterval = getGpxDataItem().getSplitInterval();
		int position = 0;

		if (splitType == GpxSplitType.DISTANCE.getType()) {
			position = distanceSplit.indexOf(splitInterval);
		} else if (splitType == GpxSplitType.TIME.getType()) {
			position = timeSplit.indexOf((int) splitInterval);
		}

		return Math.max(position, 0);
	}

	private void updateTrackColor() {
		int color = getGpxDataItem() != null ? getGpxDataItem().getColor() : 0;
		GPXFile gpxFile = getGpx();
		if (color == 0 && gpxFile != null) {
			if (gpxFile.showCurrentTrack) {
				color = app.getSettings().CURRENT_TRACK_COLOR.get();
			} else {
				color = gpxFile.getColor(0);
			}
		}
		if (color == 0) {
			RenderingRulesStorage renderer = app.getRendererRegistry().getCurrentSelectedRenderer();
			CommonPreference<String> prefColor = app.getSettings().getCustomRenderProperty(CURRENT_TRACK_COLOR_ATTR);
			color = GpxAppearanceAdapter.parseTrackColor(renderer, prefColor.get());
		}
		TrackBitmapDrawer trackDrawer = getTrackBitmapDrawer();
		if (trackDrawer != null) {
			trackDrawer.setTrackColor(color);
		}
	}

	public List<GpxSelectionHelper.GpxDisplayItem> flatten(List<GpxDisplayGroup> groups) {
		ArrayList<GpxSelectionHelper.GpxDisplayItem> list = new ArrayList<>();
		for (GpxDisplayGroup g : groups) {
			list.addAll(g.getModifiableList());
		}
		return list;
	}

	private void prepareSplitIntervalAdapterData() {
		final List<GpxDisplayGroup> groups = getDisplayGroups();

		options.add(app.getString(R.string.shared_string_none));
		distanceSplit.add(-1d);
		timeSplit.add(-1);
		addOptionSplit(30, true, groups); // 50 feet, 20 yards, 20
		// m
		addOptionSplit(60, true, groups); // 100 feet, 50 yards,
		// 50 m
		addOptionSplit(150, true, groups); // 200 feet, 100 yards,
		// 100 m
		addOptionSplit(300, true, groups); // 500 feet, 200 yards,
		// 200 m
		addOptionSplit(600, true, groups); // 1000 feet, 500 yards,
		// 500 m
		addOptionSplit(1500, true, groups); // 2000 feet, 1000 yards, 1 km
		addOptionSplit(3000, true, groups); // 1 mi, 2 km
		addOptionSplit(6000, true, groups); // 2 mi, 5 km
		addOptionSplit(15000, true, groups); // 5 mi, 10 km

		addOptionSplit(15, false, groups);
		addOptionSplit(30, false, groups);
		addOptionSplit(60, false, groups);
		addOptionSplit(120, false, groups);
		addOptionSplit(150, false, groups);
		addOptionSplit(300, false, groups);
		addOptionSplit(600, false, groups);
		addOptionSplit(900, false, groups);
		addOptionSplit(1800, false, groups);
		addOptionSplit(3600, false, groups);
	}

	private void updateSplit(@NonNull List<GpxDisplayGroup> groups, @Nullable final SelectedGpxFile selectedGpx) {
		GPXFile gpxFile = getGpx();
		TrackActivity activity = getTrackActivity();
		GpxSplitType gpxSplitType = getGpxSplitType();
		if (activity != null && gpxSplitType != null && gpxFile != null) {
			int timeSplit = 0;
			double distanceSplit = 0;
			if (gpxSplitType != GpxSplitType.NO_SPLIT && !gpxFile.showCurrentTrack) {
				timeSplit = this.timeSplit.get(selectedSplitInterval);
				distanceSplit = this.distanceSplit.get(selectedSplitInterval);
			}
			SplitTrackListener splitTrackListener = new SplitTrackListener() {

				@Override
				public void trackSplittingStarted() {
					TrackActivity activity = getTrackActivity();
					if (activity != null) {
						activity.setSupportProgressBarIndeterminateVisibility(true);
					}
				}

				@Override
				public void trackSplittingFinished() {
					TrackActivity activity = getTrackActivity();
					if (activity != null) {
						if (AndroidUtils.isActivityNotDestroyed(activity)) {
							activity.setSupportProgressBarIndeterminateVisibility(false);
						}
						if (selectedGpx != null) {
							List<GpxDisplayGroup> groups = getDisplayGroups();
							selectedGpx.setDisplayGroups(groups, app);
						}
					}
				}
			};
			new SplitTrackAsyncTask(app, gpxSplitType, groups, splitTrackListener, activity.isJoinSegments(),
					timeSplit, distanceSplit).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		}
	}

	private GpxSplitType getGpxSplitType() {
		if (selectedSplitInterval == 0) {
			return GpxSplitType.NO_SPLIT;
		} else if (distanceSplit.get(selectedSplitInterval) > 0) {
			return GpxSplitType.DISTANCE;
		} else if (timeSplit.get(selectedSplitInterval) > 0) {
			return GpxSplitType.TIME;
		}
		return null;
	}

	private void addOptionSplit(int value, boolean distance, @NonNull List<GpxDisplayGroup> model) {
		if (model.size() > 0) {
			if (distance) {
				double dvalue = OsmAndFormatter.calculateRoundedDist(value, app);
				options.add(OsmAndFormatter.getFormattedDistanceInterval(app, value));
				distanceSplit.add(dvalue);
				timeSplit.add(-1);
				if (Math.abs(model.get(0).getSplitDistance() - dvalue) < 1) {
					selectedSplitInterval = distanceSplit.size() - 1;
				}
			} else {
				options.add(OsmAndFormatter.getFormattedTimeInterval(app, value));
				distanceSplit.add(-1d);
				timeSplit.add(value);
				if (model.get(0).getSplitTime() == value) {
					selectedSplitInterval = distanceSplit.size() - 1;
				}
			}
		}
	}

	private void updateSplitInDatabase() {
		double splitInterval = 0;
		GpxSplitType splitType = null;
		if (selectedSplitInterval == 0) {
			splitType = GpxSplitType.NO_SPLIT;
			splitInterval = 0;
		} else if (distanceSplit.get(selectedSplitInterval) > 0) {
			splitType = GpxSplitType.DISTANCE;
			splitInterval = distanceSplit.get(selectedSplitInterval);
		} else if (timeSplit.get(selectedSplitInterval) > 0) {
			splitType = GpxSplitType.TIME;
			splitInterval = timeSplit.get(selectedSplitInterval);
		}
		GpxDataItem item = getGpxDataItem();
		if (item != null && splitType != null) {
			app.getGpxDbHelper().updateSplit(item, splitType, splitInterval);
		}
	}

	public void updateSplitView() {
		GPXFile gpxFile = getGpx();
		if (gpxFile != null) {
			SelectedGpxFile sf = app.getSelectedGpxHelper().selectGpxFile(gpxFile,
					((SwitchCompat) headerView.findViewById(R.id.showOnMapToggle)).isChecked(), false);
			final List<GpxDisplayGroup> groups = getDisplayGroups();
			if (groups.size() > 0) {
				updateSplit(groups, ((SwitchCompat) headerView.findViewById(R.id.showOnMapToggle)).isChecked() ? sf : null);
				if (getGpxDataItem() != null) {
					updateSplitInDatabase();
				}
			}
			updateSplitIntervalView(headerView.findViewById(R.id.split_interval_view));
		}
	}

	public void hideTransparentOverlay() {
		overlayView.setVisibility(View.GONE);
	}

	public void showTransparentOverlay() {
		overlayView.setVisibility(View.VISIBLE);
	}

	public void openFabMenu(@NonNull Context context) {
		menuFab.setImageDrawable(AppCompatResources.getDrawable(context, R.drawable.ic_action_remove_dark));
		waypointFab.setVisibility(View.VISIBLE);
		waypointTextLayout.setVisibility(View.VISIBLE);
		routePointFab.setVisibility(View.VISIBLE);
		routePointTextLayout.setVisibility(View.VISIBLE);
		lineFab.setVisibility(View.VISIBLE);
		lineTextLayout.setVisibility(View.VISIBLE);
		fabMenuOpened = true;
	}

	public void closeFabMenu(@NonNull Context context) {
		menuFab.setImageDrawable(AppCompatResources.getDrawable(context, R.drawable.ic_action_plus));
		waypointFab.setVisibility(View.GONE);
		waypointTextLayout.setVisibility(View.GONE);
		routePointFab.setVisibility(View.GONE);
		routePointTextLayout.setVisibility(View.GONE);
		lineFab.setVisibility(View.GONE);
		lineTextLayout.setVisibility(View.GONE);
		fabMenuOpened = false;
	}

	public void addPoint(PointDescription pointDescription) {
		TrackActivity activity = getTrackActivity();
		if (activity != null) {
			activity.addPoint(pointDescription);
		}
	}

	public void addNewGpxData() {
		TrackActivity activity = getTrackActivity();
		if (activity != null) {
			activity.addNewGpxData();
		}
	}

	private View.OnClickListener onFabClickListener = new View.OnClickListener() {
		@Override
		public void onClick(View view) {
			int i = view.getId();
			if (i == R.id.overlay_view) {
				hideTransparentOverlay();
				closeFabMenu(view.getContext());
			} else if (i == R.id.menu_fab) {
				if (fabMenuOpened) {
					hideTransparentOverlay();
					closeFabMenu(view.getContext());
				} else {
					showTransparentOverlay();
					openFabMenu(view.getContext());
				}
			} else if (i == R.id.waypoint_text_layout || i == R.id.waypoint_fab) {
				PointDescription pointWptDescription =
						new PointDescription(PointDescription.POINT_TYPE_WPT, app.getString(R.string.add_waypoint));
				addPoint(pointWptDescription);
			} else if (i == R.id.route_text_layout || i == R.id.route_fab || i == R.id.line_fab) {
				addNewGpxData();
			}
		}
	};

	@Override
	public void onTrackBitmapDrawing() {
		showTrackBitmapProgress();
	}

	@Override
	public void onTrackBitmapDrawn() {
		hideTrackBitmapProgress();
	}

	@Override
	public boolean isTrackBitmapSelectionSupported() {
		return trackBitmapSelectionSupported;
	}

	@Override
	public void drawTrackBitmap(Bitmap bitmap) {
		imageView.setImageDrawable(new BitmapDrawable(app.getResources(), bitmap));
	}
}
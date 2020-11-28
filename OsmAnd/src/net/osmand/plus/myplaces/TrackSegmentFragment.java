package net.osmand.plus.myplaces;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.viewpager.widget.PagerAdapter;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.listener.ChartTouchListener.ChartGesture;
import com.github.mikephil.charting.listener.OnChartGestureListener;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;

import net.osmand.AndroidUtils;
import net.osmand.GPXUtilities;
import net.osmand.GPXUtilities.GPXFile;
import net.osmand.GPXUtilities.GPXTrackAnalysis;
import net.osmand.GPXUtilities.Track;
import net.osmand.GPXUtilities.TrkSegment;
import net.osmand.GPXUtilities.WptPt;
import net.osmand.data.LatLon;
import net.osmand.data.PointDescription;
import net.osmand.plus.GPXDatabase.GpxDataItem;
import net.osmand.plus.GpxSelectionHelper.GpxDisplayGroup;
import net.osmand.plus.GpxSelectionHelper.GpxDisplayItem;
import net.osmand.plus.GpxSelectionHelper.GpxDisplayItemType;
import net.osmand.plus.GpxSelectionHelper.SelectedGpxFile;
import net.osmand.plus.OsmAndFormatter;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.UiUtilities;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.activities.TrackActivity;
import net.osmand.plus.base.OsmAndListFragment;
import net.osmand.plus.helpers.AndroidUiHelper;
import net.osmand.plus.helpers.GpxUiHelper;
import net.osmand.plus.helpers.GpxUiHelper.LineGraphType;
import net.osmand.plus.helpers.GpxUiHelper.GPXDataSetAxisType;
import net.osmand.plus.helpers.GpxUiHelper.GPXDataSetType;
import net.osmand.plus.helpers.GpxUiHelper.OrderedLineDataSet;
import net.osmand.plus.myplaces.TrackBitmapDrawer.TrackBitmapDrawerListener;
import net.osmand.plus.settings.backend.OsmandSettings;
import net.osmand.plus.track.SaveGpxAsyncTask;
import net.osmand.plus.track.SaveGpxAsyncTask.SaveGpxListener;
import net.osmand.plus.views.controls.PagerSlidingTabStrip;
import net.osmand.plus.views.controls.PagerSlidingTabStrip.CustomTabProvider;
import net.osmand.plus.views.controls.WrapContentHeightViewPager;
import net.osmand.plus.views.controls.WrapContentHeightViewPager.ViewAtPositionInterface;
import net.osmand.plus.widgets.IconPopupMenu;
import net.osmand.util.Algorithms;
import net.osmand.util.MapUtils;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.osmand.plus.helpers.GpxUiHelper.LineGraphType.ALTITUDE;
import static net.osmand.plus.helpers.GpxUiHelper.LineGraphType.SLOPE;
import static net.osmand.plus.helpers.GpxUiHelper.LineGraphType.SPEED;

public class TrackSegmentFragment extends OsmAndListFragment implements TrackBitmapDrawerListener {

	private OsmandApplication app;
	private TrackActivityFragmentAdapter fragmentAdapter;
	private SegmentGPXAdapter adapter;

	private boolean updateEnable;
	private boolean chartClicked;

	private IconPopupMenu generalPopupMenu;
	private IconPopupMenu altitudePopupMenu;

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.app = getMyApplication();
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		if (fragmentAdapter != null) {
			fragmentAdapter.onActivityCreated(savedInstanceState);
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.track_segments_tree, container, false);
		ListView listView = (ListView) view.findViewById(android.R.id.list);
		listView.setDivider(null);
		listView.setDividerHeight(0);

		fragmentAdapter = new TrackActivityFragmentAdapter(app, this, listView,
				GpxDisplayItemType.TRACK_SEGMENT);
		fragmentAdapter.setShowMapOnly(false);
		fragmentAdapter.setTrackBitmapSelectionSupported(true);
		fragmentAdapter.setShowDescriptionCard(false);
		fragmentAdapter.onCreateView(view);

		adapter = new SegmentGPXAdapter(inflater.getContext(), new ArrayList<GpxDisplayItem>());
		setListAdapter(adapter);

		return view;
	}

	@Nullable
	public TrackActivity getTrackActivity() {
		return (TrackActivity) getActivity();
	}

	public ArrayAdapter<?> getAdapter() {
		return adapter;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		menu.clear();
		GPXFile gpxFile = getGpx();
		if (gpxFile != null) {
			if (gpxFile.path != null && !gpxFile.showCurrentTrack) {
				Drawable shareIcon = app.getUIUtilities().getIcon((R.drawable.ic_action_gshare_dark));
				MenuItem item = menu.add(R.string.shared_string_share)
						.setIcon(AndroidUtils.getDrawableForDirection(app, shareIcon))
						.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
							@Override
							public boolean onMenuItemClick(MenuItem item) {
								final Uri fileUri = AndroidUtils.getUriForFile(getMyApplication(), new File(getGpx().path));
								final Intent sendIntent = new Intent(Intent.ACTION_SEND);
								sendIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
								sendIntent.setType("application/gpx+xml");
								sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
								startActivity(sendIntent);
								return true;
							}
						});
				item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
			}
			if (gpxFile.showCurrentTrack) {
				MenuItem item = menu.add(R.string.shared_string_refresh).setIcon(R.drawable.ic_action_refresh_dark)
						.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
							@Override
							public boolean onMenuItemClick(MenuItem item) {
								if (isUpdateEnable()) {
									updateContent();
									adapter.notifyDataSetChanged();
								}
								return true;
							}
						});
				item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
			}
		}
	}

	@Nullable
	private GPXFile getGpx() {
		TrackActivity activity = getTrackActivity();
		return activity != null ? activity.getGpx() : null;
	}

	@Nullable
	private GpxDataItem getGpxDataItem() {
		TrackActivity activity = getTrackActivity();
		return activity != null ? activity.getGpxDataItem() : null;
	}

	@Override
	public void onResume() {
		super.onResume();
		setUpdateEnable(true);
		updateContent();
	}

	@Override
	public void onPause() {
		super.onPause();
		setUpdateEnable(false);
		if (generalPopupMenu != null) {
			generalPopupMenu.dismiss();
		}
		if (altitudePopupMenu != null) {
			altitudePopupMenu.dismiss();
		}
		if (fragmentAdapter != null) {
			if (fragmentAdapter.splitListPopupWindow != null) {
				fragmentAdapter.splitListPopupWindow.dismiss();
			}
			if (fragmentAdapter.colorListPopupWindow != null) {
				fragmentAdapter.colorListPopupWindow.dismiss();
			}
		}
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		fragmentAdapter = null;
	}

	public boolean isUpdateEnable() {
		return updateEnable;
	}

	public void setUpdateEnable(boolean updateEnable) {
		this.updateEnable = updateEnable;
		if (fragmentAdapter != null) {
			fragmentAdapter.setUpdateEnable(updateEnable);
		}
	}

	public void updateContent() {
		adapter.clear();
		List<GpxDisplayGroup> groups = getOriginalGroups();
		if (groups != null) {
			adapter.setNotifyOnChange(false);
			List<GpxDisplayItem> items = flatten(groups);
			if (items != null) {
				for (GpxDisplayItem i : items) {
					adapter.add(i);
				}
				adapter.notifyDataSetChanged();
				if (getActivity() != null) {
					updateHeader();
				}
			}
		}
	}

	public void updateHeader() {
		if (fragmentAdapter != null) {
			fragmentAdapter.updateHeader(adapter.getCount());
		}
	}

	public void updateSplitView() {
		if (fragmentAdapter != null) {
			fragmentAdapter.updateSplitView();
		}
	}

	@Nullable
	private List<GpxDisplayItem> flatten(List<GpxDisplayGroup> groups) {
		return fragmentAdapter != null ? fragmentAdapter.flatten(groups) : null;
	}

	@Nullable
	private List<GpxDisplayGroup> getOriginalGroups() {
		return fragmentAdapter != null ? fragmentAdapter.getOriginalGroups() : null;
	}

	@Nullable
	private List<GpxDisplayGroup> getDisplayGroups() {
		return fragmentAdapter != null ? fragmentAdapter.getDisplayGroups() : null;
	}

	@Override
	public void onTrackBitmapDrawing() {
		if (fragmentAdapter != null) {
			fragmentAdapter.onTrackBitmapDrawing();
		}
	}

	@Override
	public void onTrackBitmapDrawn() {
		if (fragmentAdapter != null) {
			fragmentAdapter.onTrackBitmapDrawn();
		}
	}

	@Override
	public boolean isTrackBitmapSelectionSupported() {
		return fragmentAdapter != null && fragmentAdapter.isTrackBitmapSelectionSupported();
	}

	@Override
	public void drawTrackBitmap(Bitmap bitmap) {
		if (fragmentAdapter != null) {
			fragmentAdapter.drawTrackBitmap(bitmap);
		}
	}

	private class SegmentGPXAdapter extends ArrayAdapter<GpxDisplayItem> {

		SegmentGPXAdapter(@NonNull Context context, @NonNull List<GpxDisplayItem> items) {
			super(context, R.layout.gpx_list_item_tab_content, items);
		}

		@Override
		public boolean isEmpty() {
			return false;
		}

		@NonNull
		@Override
		public View getView(int position, View convertView, @NonNull ViewGroup parent) {
			View row = convertView;
			PagerSlidingTabStrip tabLayout;
			WrapContentHeightViewPager pager;
			boolean create = false;
			if (row == null) {
				LayoutInflater inflater = LayoutInflater.from(parent.getContext());
				row = inflater.inflate(R.layout.gpx_list_item_tab_content, parent, false);

				boolean light = app.getSettings().isLightContent();
				tabLayout = (PagerSlidingTabStrip) row.findViewById(R.id.sliding_tabs);
				tabLayout.setTabBackground(R.color.color_transparent);
				tabLayout.setIndicatorColorResource(light ? R.color.active_color_primary_light : R.color.active_color_primary_dark);
				tabLayout.setIndicatorBgColorResource(light ? R.color.divider_color_light : R.color.divider_color_dark);
				tabLayout.setIndicatorHeight(AndroidUtils.dpToPx(app, 1f));
				if (light) {
					tabLayout.setTextColor(tabLayout.getIndicatorColor());
					tabLayout.setTabInactiveTextColor(ContextCompat.getColor(row.getContext(), R.color.text_color_secondary_light));
				}
				tabLayout.setTextSize(AndroidUtils.spToPx(app, 12f));
				tabLayout.setShouldExpand(true);
				pager = (WrapContentHeightViewPager) row.findViewById(R.id.pager);
				pager.setSwipeable(false);
				pager.setOffscreenPageLimit(2);
				create = true;
			} else {
				tabLayout = (PagerSlidingTabStrip) row.findViewById(R.id.sliding_tabs);
				pager = (WrapContentHeightViewPager) row.findViewById(R.id.pager);
			}
			GpxDisplayItem item = getItem(position);
			if (item != null) {
				pager.setAdapter(new GPXItemPagerAdapter(tabLayout, item));
				if (create) {
					tabLayout.setViewPager(pager);
				} else {
					tabLayout.notifyDataSetChanged(true);
				}
			}
			return row;
		}
	}

	private enum GPXTabItemType {
		GPX_TAB_ITEM_GENERAL,
		GPX_TAB_ITEM_ALTITUDE,
		GPX_TAB_ITEM_SPEED
	}

	private class GPXItemPagerAdapter extends PagerAdapter implements CustomTabProvider, ViewAtPositionInterface {

		protected SparseArray<View> views = new SparseArray<>();
		private PagerSlidingTabStrip tabs;
		private GpxDisplayItem gpxItem;
		private GPXTabItemType[] tabTypes;
		private String[] titles;
		private Map<GPXTabItemType, List<ILineDataSet>> dataSetsMap = new HashMap<>();
		private TrkSegment segment;
		private float listViewYPos;
		private WptPt selectedWpt;

		GPXItemPagerAdapter(PagerSlidingTabStrip tabs, GpxDisplayItem gpxItem) {
			super();
			this.tabs = tabs;
			this.gpxItem = gpxItem;
			fetchTabTypes();
		}

		private void fetchTabTypes() {

			List<GPXTabItemType> tabTypeList = new ArrayList<>();
			tabTypeList.add(GPXTabItemType.GPX_TAB_ITEM_GENERAL);
			if (gpxItem != null && gpxItem.analysis != null) {
				if (gpxItem.analysis.hasElevationData) {
					tabTypeList.add(GPXTabItemType.GPX_TAB_ITEM_ALTITUDE);
				}
				if (gpxItem.analysis.isSpeedSpecified()) {
					tabTypeList.add(GPXTabItemType.GPX_TAB_ITEM_SPEED);
				}
			}
			tabTypes = tabTypeList.toArray(new GPXTabItemType[0]);

			Context context = tabs.getContext();
			titles = new String[tabTypes.length];
			for (int i = 0; i < titles.length; i++) {
				switch (tabTypes[i]) {
					case GPX_TAB_ITEM_GENERAL:
						titles[i] = context.getString(R.string.shared_string_overview);
						break;
					case GPX_TAB_ITEM_ALTITUDE:
						titles[i] = context.getString(R.string.altitude);
						break;
					case GPX_TAB_ITEM_SPEED:
						titles[i] = context.getString(R.string.map_widget_speed);
						break;
				}
			}
		}

		private List<ILineDataSet> getDataSets(LineChart chart,
		                                       GPXTabItemType tabType,
		                                       LineGraphType firstType,
		                                       LineGraphType secondType) {
			List<ILineDataSet> dataSets = dataSetsMap.get(tabType);
			if (dataSets == null && chart != null) {
				GPXTrackAnalysis analysis = gpxItem.analysis;
				GpxDataItem gpxDataItem = getGpxDataItem();
				boolean calcWithoutGaps = gpxItem.isGeneralTrack() && gpxDataItem != null && !gpxDataItem.isJoinSegments();
				dataSets = GpxUiHelper.getDataSets(chart, app, analysis, firstType, secondType, calcWithoutGaps);
				dataSetsMap.put(tabType, dataSets);
			}
			return dataSets;
		}

		private TrkSegment getTrackSegment(LineChart chart) {
			if (segment == null) {
				LineData lineData = chart.getLineData();
				List<ILineDataSet> ds = lineData != null ? lineData.getDataSets() : null;
				if (ds != null && ds.size() > 0) {
					for (GPXUtilities.Track t : gpxItem.group.getGpx().tracks) {
						for (TrkSegment s : t.segments) {
							if (s.points.size() > 0 && s.points.get(0).equals(gpxItem.analysis.locationStart)) {
								segment = s;
								break;
							}
						}
						if (segment != null) {
							break;
						}
					}
				}
			}
			return segment;
		}

		private WptPt getPoint(LineChart chart, float pos) {
			WptPt wpt = null;
			LineData lineData = chart.getLineData();
			List<ILineDataSet> ds = lineData != null ? lineData.getDataSets() : null;
			if (ds != null && ds.size() > 0) {
				TrkSegment segment = getTrackSegment(chart);
				OrderedLineDataSet dataSet = (OrderedLineDataSet) ds.get(0);
				if (gpxItem.chartAxisType == GPXDataSetAxisType.TIME) {
					float time = pos * 1000;
					for (WptPt p : segment.points) {
						if (p.time - gpxItem.analysis.startTime >= time) {
							wpt = p;
							break;
						}
					}
				} else {
					float distance = pos * dataSet.getDivX();
					double totalDistance = 0;
					for (int i = 0; i < segment.points.size(); i++) {
						WptPt currentPoint = segment.points.get(i);
						if (i != 0) {
							WptPt previousPoint = segment.points.get(i - 1);
							totalDistance += MapUtils.getDistance(previousPoint.lat, previousPoint.lon, currentPoint.lat, currentPoint.lon);
						}
						if (currentPoint.distance >= distance || Math.abs(totalDistance - distance) < 0.1) {
							wpt = currentPoint;
							break;
						}
					}
				}
			}
			return wpt;
		}

		private void scrollBy(int px) {
			getListView().setSelectionFromTop(getListView().getFirstVisiblePosition(), getListView().getChildAt(0).getTop() - px);
		}

		@Override
		public int getCount() {
			return tabTypes.length;
		}

		@Override
		public CharSequence getPageTitle(int position) {
			return titles[position];
		}

		@NonNull
		@Override
		public Object instantiateItem(@NonNull ViewGroup container, int position) {

			GPXTabItemType tabType = tabTypes[position];
			final View view;
			LayoutInflater inflater = LayoutInflater.from(container.getContext());
			switch (tabType) {
				case GPX_TAB_ITEM_GENERAL:
					view = inflater.inflate(R.layout.gpx_item_general, container, false);
					break;
				case GPX_TAB_ITEM_ALTITUDE:
					view = inflater.inflate(R.layout.gpx_item_altitude, container, false);
					break;
				case GPX_TAB_ITEM_SPEED:
					view = inflater.inflate(R.layout.gpx_item_speed, container, false);
					break;
				default:
					view = inflater.inflate(R.layout.gpx_item_general, container, false);
					break;
			}
			GPXFile gpxFile = getGpx();
			if (gpxFile != null && gpxItem != null) {
				GPXTrackAnalysis analysis = gpxItem.analysis;
				final LineChart chart = (LineChart) view.findViewById(R.id.chart);
				chart.setHighlightPerDragEnabled(chartClicked);
				chart.setOnClickListener(new View.OnClickListener() {
					@SuppressLint("ClickableViewAccessibility")
					@Override
					public void onClick(View view) {
						if (!chartClicked) {
							chartClicked = true;
							if (selectedWpt != null && fragmentAdapter != null) {
								fragmentAdapter.updateSelectedPoint(selectedWpt.lat, selectedWpt.lon);
							}
						}
					}
				});
				chart.setOnTouchListener(new View.OnTouchListener() {
					@Override
					public boolean onTouch(View v, MotionEvent event) {
						if (chartClicked) {
							getListView().requestDisallowInterceptTouchEvent(true);
							if (!chart.isHighlightPerDragEnabled()) {
								chart.setHighlightPerDragEnabled(true);
							}
							switch (event.getAction()) {
								case android.view.MotionEvent.ACTION_DOWN:
									listViewYPos = event.getRawY();
									break;
								case android.view.MotionEvent.ACTION_MOVE:
									scrollBy(Math.round(listViewYPos - event.getRawY()));
									listViewYPos = event.getRawY();
									break;
							}
						}
						return false;
					}
				});
				chart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
					@Override
					public void onValueSelected(Entry e, Highlight h) {
						WptPt wpt = getPoint(chart, h.getX());
						selectedWpt = wpt;
						if (chartClicked && wpt != null && fragmentAdapter != null) {
							fragmentAdapter.updateSelectedPoint(wpt.lat, wpt.lon);
						}
					}

					@Override
					public void onNothingSelected() {

					}
				});
				chart.setOnChartGestureListener(new OnChartGestureListener() {

					float highlightDrawX = -1;

					@Override
					public void onChartGestureStart(MotionEvent me, ChartGesture lastPerformedGesture) {
						if (chart.getHighlighted() != null && chart.getHighlighted().length > 0) {
							highlightDrawX = chart.getHighlighted()[0].getDrawX();
						} else {
							highlightDrawX = -1;
						}
					}

					@Override
					public void onChartGestureEnd(MotionEvent me, ChartGesture lastPerformedGesture) {
						gpxItem.chartMatrix = new Matrix(chart.getViewPortHandler().getMatrixTouch());
						Highlight[] highlights = chart.getHighlighted();
						if (highlights != null && highlights.length > 0) {
							gpxItem.chartHighlightPos = highlights[0].getX();
						} else {
							gpxItem.chartHighlightPos = -1;
						}
						if (chartClicked) {
							for (int i = 0; i < getCount(); i++) {
								View v = getViewAtPosition(i);
								if (v != view) {
									updateChart(i);
								}
							}
						}
					}

					@Override
					public void onChartLongPressed(MotionEvent me) {
					}

					@Override
					public void onChartDoubleTapped(MotionEvent me) {
					}

					@Override
					public void onChartSingleTapped(MotionEvent me) {
					}

					@Override
					public void onChartFling(MotionEvent me1, MotionEvent me2, float velocityX, float velocityY) {
					}

					@Override
					public void onChartScale(MotionEvent me, float scaleX, float scaleY) {
					}

					@Override
					public void onChartTranslate(MotionEvent me, float dX, float dY) {
						if (chartClicked && highlightDrawX != -1) {
							Highlight h = chart.getHighlightByTouchPoint(highlightDrawX, 0f);
							if (h != null) {
								chart.highlightValue(h);
								WptPt wpt = getPoint(chart, h.getX());
								if (wpt != null && fragmentAdapter != null) {
									fragmentAdapter.updateSelectedPoint(wpt.lat, wpt.lon);
								}
							}
						}
					}
				});

				final UiUtilities ic = app.getUIUtilities();
				switch (tabType) {
					case GPX_TAB_ITEM_GENERAL:
						if (analysis != null) {
							if (analysis.hasElevationData || analysis.hasSpeedData) {
								GpxUiHelper.setupGPXChart(app, chart, 4);
								chart.setData(new LineData(getDataSets(chart, GPXTabItemType.GPX_TAB_ITEM_GENERAL, ALTITUDE, SPEED)));
								updateChart(chart);
								chart.setVisibility(View.VISIBLE);
							} else {
								chart.setVisibility(View.GONE);
							}

							((ImageView) view.findViewById(R.id.distance_icon))
									.setImageDrawable(ic.getThemedIcon(R.drawable.ic_action_polygom_dark));
							((ImageView) view.findViewById(R.id.duration_icon))
									.setImageDrawable(ic.getThemedIcon(R.drawable.ic_action_time_span));
							((ImageView) view.findViewById(R.id.start_time_icon))
									.setImageDrawable(ic.getThemedIcon(R.drawable.ic_action_time_start));
							((ImageView) view.findViewById(R.id.end_time_icon))
									.setImageDrawable(ic.getThemedIcon(R.drawable.ic_action_time_end));

							view.findViewById(R.id.gpx_join_gaps_container).setOnClickListener(new View.OnClickListener() {
								@Override
								public void onClick(View v) {
									TrackActivity activity = getTrackActivity();
									if (activity != null && activity.setJoinSegments(!activity.isJoinSegments())) {
										updateContent();
										for (int i = 0; i < getCount(); i++) {
											View view = getViewAtPosition(i);
											updateJoinGapsInfo(view, i);
										}
									}
								}
							});
							if (analysis.timeSpan > 0) {
								DateFormat tf = SimpleDateFormat.getTimeInstance(DateFormat.SHORT);
								DateFormat df = SimpleDateFormat.getDateInstance(DateFormat.MEDIUM);

								Date start = new Date(analysis.startTime);
								((TextView) view.findViewById(R.id.start_time_text)).setText(tf.format(start));
								((TextView) view.findViewById(R.id.start_date_text)).setText(df.format(start));
								Date end = new Date(analysis.endTime);
								((TextView) view.findViewById(R.id.end_time_text)).setText(tf.format(end));
								((TextView) view.findViewById(R.id.end_date_text)).setText(df.format(end));
							} else {
								view.findViewById(R.id.list_divider).setVisibility(View.GONE);
								view.findViewById(R.id.start_end_time).setVisibility(View.GONE);
							}
						} else {
							chart.setVisibility(View.GONE);
							view.findViewById(R.id.distance_time_span).setVisibility(View.GONE);
							view.findViewById(R.id.list_divider).setVisibility(View.GONE);
							view.findViewById(R.id.start_end_time).setVisibility(View.GONE);
						}
						updateJoinGapsInfo(view, position);
						view.findViewById(R.id.analyze_on_map).setOnClickListener(new View.OnClickListener() {
							@Override
							public void onClick(View v) {
								openAnalyzeOnMap(GPXTabItemType.GPX_TAB_ITEM_GENERAL);
							}
						});
						if (gpxFile.showCurrentTrack) {
							view.findViewById(R.id.split_interval).setVisibility(View.GONE);
						} else {
							view.findViewById(R.id.split_interval).setOnClickListener(new View.OnClickListener() {
								@Override
								public void onClick(View view) {
									openSplitIntervalScreen();
								}
							});
						}
						if (!gpxItem.group.getTrack().generalTrack) {
							((ImageView) view.findViewById(R.id.overflow_menu)).setImageDrawable(ic.getThemedIcon(R.drawable.ic_overflow_menu_white));
							view.findViewById(R.id.overflow_menu).setVisibility(View.VISIBLE);
							view.findViewById(R.id.overflow_menu).setOnClickListener(new View.OnClickListener() {
								@Override
								public void onClick(View view) {
									TrackActivity activity = getTrackActivity();
									if (activity != null) {
										generalPopupMenu = new IconPopupMenu(activity, view.findViewById(R.id.overflow_menu));
										Menu menu = generalPopupMenu.getMenu();
										generalPopupMenu.getMenuInflater().inflate(R.menu.track_segment_menu, menu);
										menu.findItem(R.id.action_edit).setIcon(ic.getThemedIcon(R.drawable.ic_action_edit_dark));
										menu.findItem(R.id.action_delete).setIcon(ic.getThemedIcon(R.drawable.ic_action_remove_dark));
										generalPopupMenu.setOnMenuItemClickListener(new IconPopupMenu.OnMenuItemClickListener() {
											@Override
											public boolean onMenuItemClick(MenuItem item) {
												int i = item.getItemId();
												if (i == R.id.action_edit) {
													editSegment();
													return true;
												} else if (i == R.id.action_delete) {
													TrackActivity activity = getTrackActivity();
													if (activity != null) {
														AlertDialog.Builder builder = new AlertDialog.Builder(activity);
														builder.setMessage(R.string.recording_delete_confirm);
														builder.setPositiveButton(R.string.shared_string_yes, new DialogInterface.OnClickListener() {
															@Override
															public void onClick(DialogInterface dialog, int which) {
																deleteAndSaveSegment();
															}
														});
														builder.setNegativeButton(R.string.shared_string_cancel, null);
														builder.show();
													}
													return true;
												}
												return false;
											}
										});
										generalPopupMenu.show();
									}
								}
							});
						} else {
							view.findViewById(R.id.overflow_menu).setVisibility(View.GONE);
						}

						break;
					case GPX_TAB_ITEM_ALTITUDE:
						if (analysis != null) {
							if (analysis.hasElevationData) {
								GpxUiHelper.setupGPXChart(app, chart, 4);
								chart.setData(new LineData(getDataSets(chart, GPXTabItemType.GPX_TAB_ITEM_ALTITUDE, ALTITUDE, SLOPE)));
								updateChart(chart);
								chart.setVisibility(View.VISIBLE);
							} else {
								chart.setVisibility(View.GONE);
							}
							((ImageView) view.findViewById(R.id.average_icon))
									.setImageDrawable(ic.getThemedIcon(R.drawable.ic_action_altitude_average));
							((ImageView) view.findViewById(R.id.range_icon))
									.setImageDrawable(ic.getThemedIcon(R.drawable.ic_action_altitude_average));
							((ImageView) view.findViewById(R.id.ascent_icon))
									.setImageDrawable(ic.getThemedIcon(R.drawable.ic_action_altitude_ascent));
							((ImageView) view.findViewById(R.id.descent_icon))
									.setImageDrawable(ic.getThemedIcon(R.drawable.ic_action_altitude_descent));

							String min = OsmAndFormatter.getFormattedAlt(analysis.minElevation, app);
							String max = OsmAndFormatter.getFormattedAlt(analysis.maxElevation, app);
							String asc = OsmAndFormatter.getFormattedAlt(analysis.diffElevationUp, app);
							String desc = OsmAndFormatter.getFormattedAlt(analysis.diffElevationDown, app);

							((TextView) view.findViewById(R.id.average_text))
									.setText(OsmAndFormatter.getFormattedAlt(analysis.avgElevation, app));
							((TextView) view.findViewById(R.id.range_text)).setText(String.format("%s - %s", min, max));
							((TextView) view.findViewById(R.id.ascent_text)).setText(asc);
							((TextView) view.findViewById(R.id.descent_text)).setText(desc);

							view.findViewById(R.id.gpx_join_gaps_container).setOnClickListener(new View.OnClickListener() {
								@Override
								public void onClick(View v) {
									TrackActivity activity = getTrackActivity();
									if (activity != null && activity.setJoinSegments(!activity.isJoinSegments())) {
										updateSplitView();
										for (int i = 0; i < getCount(); i++) {
											View view = getViewAtPosition(i);
											updateJoinGapsInfo(view, i);
										}
									}
								}
							});
						} else {
							chart.setVisibility(View.GONE);
							view.findViewById(R.id.average_range).setVisibility(View.GONE);
							view.findViewById(R.id.list_divider).setVisibility(View.GONE);
							view.findViewById(R.id.ascent_descent).setVisibility(View.GONE);
						}
						updateJoinGapsInfo(view, position);
						view.findViewById(R.id.analyze_on_map).setOnClickListener(new View.OnClickListener() {
							@Override
							public void onClick(View v) {
								openAnalyzeOnMap(GPXTabItemType.GPX_TAB_ITEM_ALTITUDE);
							}
						});
						if (gpxFile.showCurrentTrack) {
							view.findViewById(R.id.split_interval).setVisibility(View.GONE);
						} else {
							view.findViewById(R.id.split_interval).setOnClickListener(new View.OnClickListener() {
								@Override
								public void onClick(View view) {
									openSplitIntervalScreen();
								}
							});
						}
						if (!gpxItem.group.getTrack().generalTrack) {
							((ImageView) view.findViewById(R.id.overflow_menu)).setImageDrawable(ic.getThemedIcon(R.drawable.ic_overflow_menu_white));
							view.findViewById(R.id.overflow_menu).setVisibility(View.VISIBLE);
							view.findViewById(R.id.overflow_menu).setOnClickListener(new View.OnClickListener() {
								@Override
								public void onClick(View view) {
									TrackActivity activity = getTrackActivity();
									if (activity != null) {
										altitudePopupMenu = new IconPopupMenu(activity, view.findViewById(R.id.overflow_menu));
										Menu menu = altitudePopupMenu.getMenu();
										altitudePopupMenu.getMenuInflater().inflate(R.menu.track_segment_menu, menu);
										menu.findItem(R.id.action_edit).setIcon(ic.getThemedIcon(R.drawable.ic_action_edit_dark));
										menu.findItem(R.id.action_delete).setIcon(ic.getThemedIcon(R.drawable.ic_action_remove_dark));
										altitudePopupMenu.setOnMenuItemClickListener(new IconPopupMenu.OnMenuItemClickListener() {
											@Override
											public boolean onMenuItemClick(MenuItem item) {
												int i = item.getItemId();
												if (i == R.id.action_edit) {
													editSegment();
													return true;
												} else if (i == R.id.action_delete) {
													deleteAndSaveSegment();
													return true;
												}
												return false;
											}
										});
										altitudePopupMenu.show();
									}
								}
							});
						} else {
							view.findViewById(R.id.overflow_menu).setVisibility(View.GONE);
						}

						break;
					case GPX_TAB_ITEM_SPEED:
						if (analysis != null && analysis.isSpeedSpecified()) {
							if (analysis.hasSpeedData) {
								GpxUiHelper.setupGPXChart(app, chart, 4);
								chart.setData(new LineData(getDataSets(chart, GPXTabItemType.GPX_TAB_ITEM_SPEED, SPEED, null)));
								updateChart(chart);
								chart.setVisibility(View.VISIBLE);
							} else {
								chart.setVisibility(View.GONE);
							}
							((ImageView) view.findViewById(R.id.average_icon))
									.setImageDrawable(ic.getThemedIcon(R.drawable.ic_action_speed));
							((ImageView) view.findViewById(R.id.max_icon))
									.setImageDrawable(ic.getThemedIcon(R.drawable.ic_action_max_speed));
							((ImageView) view.findViewById(R.id.time_moving_icon))
									.setImageDrawable(ic.getThemedIcon(R.drawable.ic_action_time_span));
							((ImageView) view.findViewById(R.id.distance_icon))
									.setImageDrawable(ic.getThemedIcon(R.drawable.ic_action_polygom_dark));

							String avg = OsmAndFormatter.getFormattedSpeed(analysis.avgSpeed, app);
							String max = OsmAndFormatter.getFormattedSpeed(analysis.maxSpeed, app);

							((TextView) view.findViewById(R.id.average_text)).setText(avg);
							((TextView) view.findViewById(R.id.max_text)).setText(max);

							view.findViewById(R.id.gpx_join_gaps_container).setOnClickListener(new View.OnClickListener() {
								@Override
								public void onClick(View v) {
									TrackActivity activity = getTrackActivity();
									if (activity != null && activity.setJoinSegments(!activity.isJoinSegments())) {
										updateSplitView();
										for (int i = 0; i < getCount(); i++) {
											View view = getViewAtPosition(i);
											updateJoinGapsInfo(view, i);
										}
									}
								}
							});
						} else {
							chart.setVisibility(View.GONE);
							view.findViewById(R.id.average_max).setVisibility(View.GONE);
							view.findViewById(R.id.list_divider).setVisibility(View.GONE);
							view.findViewById(R.id.time_distance).setVisibility(View.GONE);
						}
						updateJoinGapsInfo(view, position);
						view.findViewById(R.id.analyze_on_map).setOnClickListener(new View.OnClickListener() {
							@Override
							public void onClick(View v) {
								openAnalyzeOnMap(GPXTabItemType.GPX_TAB_ITEM_SPEED);
							}
						});
						if (gpxFile.showCurrentTrack) {
							view.findViewById(R.id.split_interval).setVisibility(View.GONE);
						} else {
							view.findViewById(R.id.split_interval).setOnClickListener(new View.OnClickListener() {
								@Override
								public void onClick(View view) {
									openSplitIntervalScreen();
								}
							});
						}
						if (!gpxItem.group.getTrack().generalTrack) {
							((ImageView) view.findViewById(R.id.overflow_menu)).setImageDrawable(ic.getThemedIcon(R.drawable.ic_overflow_menu_white));
							view.findViewById(R.id.overflow_menu).setVisibility(View.VISIBLE);
							view.findViewById(R.id.overflow_menu).setOnClickListener(new View.OnClickListener() {
								@Override
								public void onClick(View view) {
									TrackActivity activity = getTrackActivity();
									if (activity != null) {
										IconPopupMenu popupMenu = new IconPopupMenu(activity, view.findViewById(R.id.overflow_menu));
										Menu menu = popupMenu.getMenu();
										popupMenu.getMenuInflater().inflate(R.menu.track_segment_menu, menu);
										menu.findItem(R.id.action_edit).setIcon(ic.getThemedIcon(R.drawable.ic_action_edit_dark));
										menu.findItem(R.id.action_delete).setIcon(ic.getThemedIcon(R.drawable.ic_action_remove_dark));
										popupMenu.setOnMenuItemClickListener(new IconPopupMenu.OnMenuItemClickListener() {
											@Override
											public boolean onMenuItemClick(MenuItem item) {
												int i = item.getItemId();
												if (i == R.id.action_edit) {
													editSegment();
													return true;
												} else if (i == R.id.action_delete) {
													deleteAndSaveSegment();
													return true;
												}
												return false;
											}
										});
										popupMenu.show();
									}
								}
							});
						} else {
							view.findViewById(R.id.overflow_menu).setVisibility(View.GONE);
						}

						break;
				}
			}
			container.addView(view, 0);
			views.put(position, view);
			return view;
		}

		private void editSegment() {
			TrkSegment segment = getTrkSegment();
			if (segment != null && fragmentAdapter != null) {
				fragmentAdapter.addNewGpxData();
			}
		}

		private void deleteAndSaveSegment() {
			TrackActivity trackActivity = getTrackActivity();
			if (trackActivity != null && deleteSegment()) {
				GPXFile gpx = getGpx();
				if (gpx != null && fragmentAdapter != null) {
					boolean showOnMap = fragmentAdapter.isShowOnMap();
					SelectedGpxFile selectedGpxFile = app.getSelectedGpxHelper().selectGpxFile(gpx, showOnMap, false);
					saveGpx(showOnMap ? selectedGpxFile : null, gpx);
				}
			}
		}

		private boolean deleteSegment() {
			TrkSegment segment = getTrkSegment();
			if (segment != null) {
				GPXFile gpx = getGpx();
				if (gpx != null) {
					return gpx.removeTrkSegment(segment);
				}
			}
			return false;
		}

		@Override
		public void destroyItem(@NonNull ViewGroup collection, int position, @NonNull Object view) {
			views.remove(position);
			collection.removeView((View) view);
		}

		@Override
		public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
			return view == object;
		}

		@Override
		public View getCustomTabView(@NonNull ViewGroup parent, int position) {
			View tab = LayoutInflater.from(parent.getContext()).inflate(R.layout.gpx_tab, parent, false);
			tab.setTag(tabTypes[position].name());
			deselect(tab);
			return tab;
		}

		private int getImageId(GPXTabItemType tabType) {
			int imageId;
			switch (tabType) {
				case GPX_TAB_ITEM_GENERAL:
					imageId = R.drawable.ic_action_polygom_dark;
					break;
				case GPX_TAB_ITEM_ALTITUDE:
					imageId = R.drawable.ic_action_altitude_average;
					break;
				case GPX_TAB_ITEM_SPEED:
					imageId = R.drawable.ic_action_speed;
					break;
				default:
					imageId = R.drawable.ic_action_folder_stroke;
			}
			return imageId;
		}

		@Override
		public void select(View tab) {
			GPXTabItemType tabType = GPXTabItemType.valueOf((String) tab.getTag());
			ImageView img = (ImageView) tab.findViewById(R.id.tab_image);
			int imageId = getImageId(tabType);
			switch (tabs.getTabSelectionType()) {
				case ALPHA:
					img.setAlpha(tabs.getTabTextSelectedAlpha());
					break;
				case SOLID_COLOR:
					img.setImageDrawable(app.getUIUtilities().getPaintedIcon(imageId, tabs.getTextColor()));
					break;
			}
		}

		@Override
		public void deselect(View tab) {
			GPXTabItemType tabType = GPXTabItemType.valueOf((String) tab.getTag());
			ImageView img = (ImageView) tab.findViewById(R.id.tab_image);
			int imageId = getImageId(tabType);
			switch (tabs.getTabSelectionType()) {
				case ALPHA:
					img.setAlpha(tabs.getTabTextAlpha());
					break;
				case SOLID_COLOR:
					img.setImageDrawable(app.getUIUtilities().getPaintedIcon(imageId, tabs.getTabInactiveTextColor()));
					break;
			}
		}

		@Override
		public View getViewAtPosition(int position) {
			return views.get(position);
		}

		void updateChart(int position) {
			View view = getViewAtPosition(position);
			if (view != null) {
				updateChart((LineChart) view.findViewById(R.id.chart));
			}
		}

		void updateJoinGapsInfo(View view, int position) {
			TrackActivity activity = getTrackActivity();
			if (view != null && activity != null) {
				GPXTrackAnalysis analysis = gpxItem.analysis;
				GPXTabItemType tabType = tabTypes[position];
				boolean visible = gpxItem.isGeneralTrack() && analysis != null && tabType.equals(GPXTabItemType.GPX_TAB_ITEM_GENERAL);
				AndroidUiHelper.updateVisibility(view.findViewById(R.id.gpx_join_gaps_container), visible);
				boolean joinSegments = activity.isJoinSegments();
				((SwitchCompat) view.findViewById(R.id.gpx_join_gaps_switch)).setChecked(joinSegments);
				if (analysis != null) {
					if (tabType.equals(GPXTabItemType.GPX_TAB_ITEM_GENERAL)) {
						float totalDistance = !joinSegments && gpxItem.isGeneralTrack() ? analysis.totalDistanceWithoutGaps : analysis.totalDistance;
						float timeSpan = !joinSegments && gpxItem.isGeneralTrack() ? analysis.timeSpanWithoutGaps : analysis.timeSpan;

						((TextView) view.findViewById(R.id.distance_text)).setText(OsmAndFormatter.getFormattedDistance(totalDistance, app));
						((TextView) view.findViewById(R.id.duration_text)).setText(Algorithms.formatDuration((int) (timeSpan / 1000), app.accessibilityEnabled()));
					} else if (tabType.equals(GPXTabItemType.GPX_TAB_ITEM_SPEED)) {
						long timeMoving = !joinSegments && gpxItem.isGeneralTrack() ? analysis.timeMovingWithoutGaps : analysis.timeMoving;
						float totalDistanceMoving = !joinSegments && gpxItem.isGeneralTrack() ? analysis.totalDistanceMovingWithoutGaps : analysis.totalDistanceMoving;

						((TextView) view.findViewById(R.id.time_moving_text)).setText(Algorithms.formatDuration((int) (timeMoving / 1000), app.accessibilityEnabled()));
						((TextView) view.findViewById(R.id.distance_text)).setText(OsmAndFormatter.getFormattedDistance(totalDistanceMoving, app));
					}
				}
			}
		}

		void updateChart(LineChart chart) {
			if (chart != null && !chart.isEmpty()) {
				if (gpxItem.chartMatrix != null) {
					chart.getViewPortHandler().refresh(new Matrix(gpxItem.chartMatrix), chart, true);
				}
				if (gpxItem.chartHighlightPos != -1) {
					chart.highlightValue(gpxItem.chartHighlightPos, 0);
				} else {
					chart.highlightValue(null);
				}
			}
		}

		private TrkSegment getTrkSegment() {
			for (Track t : gpxItem.group.getGpx().tracks) {
				if (!t.generalTrack && !gpxItem.isGeneralTrack() || t.generalTrack && gpxItem.isGeneralTrack()) {
					for (TrkSegment s : t.segments) {
						if (s.points.size() > 0 && s.points.get(0).equals(gpxItem.analysis.locationStart)) {
							return s;
						}
					}
				}
			}
			return null;
		}

		void openAnalyzeOnMap(GPXTabItemType tabType) {
			LatLon location = null;
			WptPt wpt = null;
			gpxItem.chartTypes = null;
			List<ILineDataSet> ds = getDataSets(null, tabType, null, null);
			if (ds != null && ds.size() > 0) {
				gpxItem.chartTypes = new GPXDataSetType[ds.size()];
				for (int i = 0; i < ds.size(); i++) {
					OrderedLineDataSet orderedDataSet = (OrderedLineDataSet) ds.get(i);
					gpxItem.chartTypes[i] = orderedDataSet.getDataSetType();
				}
				if (gpxItem.chartHighlightPos != -1) {
					TrkSegment segment = null;
					for (Track t : gpxItem.group.getGpx().tracks) {
						for (TrkSegment s : t.segments) {
							if (s.points.size() > 0 && s.points.get(0).equals(gpxItem.analysis.locationStart)) {
								segment = s;
								break;
							}
						}
						if (segment != null) {
							break;
						}
					}
					if (segment != null) {
						OrderedLineDataSet dataSet = (OrderedLineDataSet) ds.get(0);
						float distance = gpxItem.chartHighlightPos * dataSet.getDivX();
						for (WptPt p : segment.points) {
							if (p.distance >= distance) {
								wpt = p;
								break;
							}
						}
						if (wpt != null) {
							location = new LatLon(wpt.lat, wpt.lon);
						}
					}
				}
			}
			if (location == null) {
				location = new LatLon(gpxItem.locationStart.lat, gpxItem.locationStart.lon);
			}
			if (wpt != null) {
				gpxItem.locationOnMap = wpt;
			} else {
				gpxItem.locationOnMap = gpxItem.locationStart;
			}

			final OsmandSettings settings = app.getSettings();
			settings.setMapLocationToShow(location.getLatitude(), location.getLongitude(),
					settings.getLastKnownMapZoom(),
					new PointDescription(PointDescription.POINT_TYPE_WPT, gpxItem.name),
					false,
					gpxItem);

			MapActivity.launchMapActivityMoveToTop(getActivity());
		}

		private	void openSplitIntervalScreen() {
			TrackActivity activity = getTrackActivity();
			if (activity != null) {
				SplitSegmentDialogFragment.showInstance(activity, gpxItem, getTrkSegment());
			}
		}
	}

	private void saveGpx(final SelectedGpxFile selectedGpxFile, GPXFile gpxFile) {
		new SaveGpxAsyncTask(gpxFile, new SaveGpxListener() {
			@Override
			public void gpxSavingStarted() {
				TrackActivity activity = getTrackActivity();
				if (activity != null && AndroidUtils.isActivityNotDestroyed(activity)) {
					activity.setSupportProgressBarIndeterminateVisibility(true);
				}
			}

			@Override
			public void gpxSavingFinished(Exception errorMessage) {
				TrackActivity activity = getTrackActivity();
				if (activity != null) {
					if (selectedGpxFile != null) {
						List<GpxDisplayGroup> groups = getDisplayGroups();
						if (groups != null) {
							selectedGpxFile.setDisplayGroups(groups, app);
							selectedGpxFile.processPoints(app);
						}
					}
					updateContent();
					if (AndroidUtils.isActivityNotDestroyed(activity)) {
						activity.setSupportProgressBarIndeterminateVisibility(false);
					}
				}
			}
		}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}
}
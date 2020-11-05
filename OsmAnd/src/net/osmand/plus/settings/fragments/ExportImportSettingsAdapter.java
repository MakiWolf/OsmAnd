package net.osmand.plus.settings.fragments;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.widget.CompoundButtonCompat;

import net.osmand.AndroidUtils;
import net.osmand.IndexConstants;
import net.osmand.PlatformUtil;
import net.osmand.map.ITileSource;
import net.osmand.plus.FavouritesDbHelper.FavoriteGroup;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.UiUtilities;
import net.osmand.plus.activities.OsmandBaseExpandableListAdapter;
import net.osmand.plus.audionotes.AudioVideoNotesPlugin;
import net.osmand.plus.helpers.AvoidSpecificRoads.AvoidRoadInfo;
import net.osmand.plus.helpers.FileNameTranslationHelper;
import net.osmand.plus.helpers.GpxUiHelper;
import net.osmand.plus.osmedit.OpenstreetmapPoint;
import net.osmand.plus.osmedit.OsmEditingPlugin;
import net.osmand.plus.osmedit.OsmNotesPoint;
import net.osmand.plus.poi.PoiUIFilter;
import net.osmand.plus.profiles.ProfileIconColors;
import net.osmand.plus.profiles.RoutingProfileDataObject.RoutingProfilesResources;
import net.osmand.plus.quickaction.QuickAction;
import net.osmand.plus.render.RenderingIcons;
import net.osmand.plus.settings.backend.ApplicationMode;
import net.osmand.plus.settings.backend.ApplicationMode.ApplicationModeBean;
import net.osmand.plus.settings.backend.ExportSettingsType;
import net.osmand.plus.settings.backend.backup.FileSettingsItem;
import net.osmand.plus.settings.backend.backup.GlobalSettingsItem;
import net.osmand.util.Algorithms;
import net.osmand.view.ThreeStateCheckbox;

import org.apache.commons.logging.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.osmand.plus.settings.backend.ExportSettingsType.OFFLINE_MAPS;
import static net.osmand.plus.settings.backend.backup.FileSettingsItem.FileSubtype;
import static net.osmand.view.ThreeStateCheckbox.State.CHECKED;
import static net.osmand.view.ThreeStateCheckbox.State.MISC;
import static net.osmand.view.ThreeStateCheckbox.State.UNCHECKED;

class ExportImportSettingsAdapter extends OsmandBaseExpandableListAdapter {

	private static final Log LOG = PlatformUtil.getLog(ExportImportSettingsAdapter.class.getName());
	private OsmandApplication app;
	private UiUtilities uiUtilities;
	private List<? super Object> data;
	private Map<ExportSettingsType, List<?>> itemsMap;
	private List<ExportSettingsType> itemsTypes;
	private boolean nightMode;
	private boolean importState;
	private int activeColorRes;
	private int secondaryColorRes;

	ExportImportSettingsAdapter(OsmandApplication app, boolean nightMode, boolean importState) {
		this.app = app;
		this.nightMode = nightMode;
		this.importState = importState;
		this.itemsMap = new HashMap<>();
		this.itemsTypes = new ArrayList<>();
		this.data = new ArrayList<>();
		uiUtilities = app.getUIUtilities();
		activeColorRes = nightMode
				? R.color.icon_color_active_dark
				: R.color.icon_color_active_light;
		secondaryColorRes = nightMode
				? R.color.icon_color_secondary_dark
				: R.color.icon_color_secondary_light;
	}

	@Override
	public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
		View group = convertView;
		if (group == null) {
			LayoutInflater inflater = UiUtilities.getInflater(app, nightMode);
			group = inflater.inflate(R.layout.profile_data_list_item_group, parent, false);
		}

		boolean isLastGroup = groupPosition == getGroupCount() - 1;
		final ExportSettingsType type = itemsTypes.get(groupPosition);

		TextView titleTv = group.findViewById(R.id.title_tv);
		TextView subTextTv = group.findViewById(R.id.sub_text_tv);
		final ThreeStateCheckbox checkBox = group.findViewById(R.id.check_box);
		FrameLayout checkBoxContainer = group.findViewById(R.id.check_box_container);
		ImageView expandIv = group.findViewById(R.id.explist_indicator);
		View lineDivider = group.findViewById(R.id.divider);
		View cardTopDivider = group.findViewById(R.id.card_top_divider);
		View cardBottomDivider = group.findViewById(R.id.card_bottom_divider);

		titleTv.setText(getGroupTitle(type));
		lineDivider.setVisibility(importState || isExpanded || isLastGroup ? View.GONE : View.VISIBLE);
		cardTopDivider.setVisibility(importState ? View.VISIBLE : View.GONE);
		cardBottomDivider.setVisibility(importState && !isExpanded ? View.VISIBLE : View.GONE);

		final List<?> listItems = itemsMap.get(type);
		subTextTv.setText(getSelectedItemsAmount(listItems, type));

		if (data.containsAll(listItems)) {
			checkBox.setState(CHECKED);
		} else {
			boolean contains = false;
			for (Object object : listItems) {
				if (data.contains(object)) {
					contains = true;
					break;
				}
			}
			checkBox.setState(contains ? MISC : UNCHECKED);
		}
		int checkBoxColor = checkBox.getState() == UNCHECKED ? secondaryColorRes : activeColorRes;
		CompoundButtonCompat.setButtonTintList(checkBox, ColorStateList.valueOf(ContextCompat.getColor(app, checkBoxColor)));
		checkBoxContainer.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				checkBox.performClick();
				if (checkBox.getState() == CHECKED) {
					for (Object object : listItems) {
						if (!data.contains(object)) {
							data.add(object);
						}
					}
				} else {
					data.removeAll(listItems);
				}
				notifyDataSetChanged();
			}
		});
		adjustIndicator(app, groupPosition, isExpanded, group, nightMode);
		return group;
	}

	@Override
	public View getChildView(int groupPosition, final int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
		View child = convertView;
		if (child == null) {
			LayoutInflater inflater = UiUtilities.getInflater(app, nightMode);
			child = inflater.inflate(R.layout.profile_data_list_item_child, parent, false);
		}
		final Object currentItem = itemsMap.get(itemsTypes.get(groupPosition)).get(childPosition);

		boolean isLastGroup = groupPosition == getGroupCount() - 1;
		boolean itemSelected = data.contains(currentItem);
		final ExportSettingsType type = itemsTypes.get(groupPosition);

		TextView title = child.findViewById(R.id.title_tv);
		TextView subText = child.findViewById(R.id.sub_title_tv);
		subText.setVisibility(View.GONE);
		final CheckBox checkBox = child.findViewById(R.id.check_box);
		ImageView icon = child.findViewById(R.id.icon);
		View lineDivider = child.findViewById(R.id.divider);
		View cardBottomDivider = child.findViewById(R.id.card_bottom_divider);

		lineDivider.setVisibility(!importState && isLastChild && !isLastGroup ? View.VISIBLE : View.GONE);
		cardBottomDivider.setVisibility(importState && isLastChild ? View.VISIBLE : View.GONE);
		int checkBoxColor = itemSelected ? activeColorRes : secondaryColorRes;
		CompoundButtonCompat.setButtonTintList(checkBox, ColorStateList.valueOf(ContextCompat.getColor(app, checkBoxColor)));

		checkBox.setChecked(itemSelected);
		checkBox.setClickable(false);
		child.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (data.contains(currentItem)) {
					data.remove(currentItem);
				} else {
					data.add(currentItem);
				}
				notifyDataSetChanged();
			}
		});

		switch (type) {
			case PROFILE:
				ApplicationModeBean modeBean = (ApplicationModeBean) currentItem;
				String profileName = modeBean.userProfileName;
				if (Algorithms.isEmpty(profileName)) {
					ApplicationMode appMode = ApplicationMode.valueOfStringKey(modeBean.stringKey, null);
					profileName = app.getString(appMode.getNameKeyResource());
				}
				title.setText(profileName);
				String routingProfile = "";
				String routingProfileValue = modeBean.routingProfile;
				if (!routingProfileValue.isEmpty()) {
					try {
						routingProfile = app.getString(RoutingProfilesResources.valueOf(routingProfileValue.toUpperCase()).getStringRes());
						routingProfile = Algorithms.capitalizeFirstLetterAndLowercase(routingProfile);
					} catch (IllegalArgumentException e) {
						routingProfile = Algorithms.capitalizeFirstLetterAndLowercase(routingProfileValue);
						LOG.error("Error trying to get routing resource for " + routingProfileValue + "\n" + e);
					}
				}
				if (!Algorithms.isEmpty(routingProfile)) {
					subText.setText(String.format(
							app.getString(R.string.ltr_or_rtl_combine_via_colon),
							app.getString(R.string.nav_type_hint),
							routingProfile));
					subText.setVisibility(View.VISIBLE);
				}
				int profileIconRes = AndroidUtils.getDrawableId(app, modeBean.iconName);
				ProfileIconColors iconColor = modeBean.iconColor;
				icon.setImageDrawable(uiUtilities.getIcon(profileIconRes, iconColor.getColor(nightMode)));
				break;
			case QUICK_ACTIONS:
				title.setText(((QuickAction) currentItem).getName(app.getApplicationContext()));
				setupIcon(icon, ((QuickAction) currentItem).getIconRes(), itemSelected);
				break;
			case POI_TYPES:
				title.setText(((PoiUIFilter) currentItem).getName());
				int iconRes = RenderingIcons.getBigIconResourceId(((PoiUIFilter) currentItem).getIconId());
				setupIcon(icon, iconRes != 0 ? iconRes : R.drawable.ic_action_user, itemSelected);
				break;
			case MAP_SOURCES:
				title.setText(((ITileSource) currentItem).getName());
				setupIcon(icon, R.drawable.ic_map, itemSelected);
				break;
			case CUSTOM_RENDER_STYLE:
				String renderName = ((File) currentItem).getName();
				renderName = renderName.replace('_', ' ').replaceAll(IndexConstants.RENDERER_INDEX_EXT, "");
				title.setText(renderName);
				setupIcon(icon, R.drawable.ic_action_map_style, itemSelected);
				break;
			case CUSTOM_ROUTING:
				String routingName = ((File) currentItem).getName();
				routingName = routingName.replace('_', ' ').replaceAll(".xml", "");
				title.setText(routingName);
				setupIcon(icon, R.drawable.ic_action_route_distance, itemSelected);
				break;
			case AVOID_ROADS:
				AvoidRoadInfo avoidRoadInfo = (AvoidRoadInfo) currentItem;
				title.setText(avoidRoadInfo.name);
				setupIcon(icon, R.drawable.ic_action_alert, itemSelected);
				break;
			case MULTIMEDIA_NOTES:
				File file = (File) currentItem;
				title.setText(file.getName());
				int iconId = AudioVideoNotesPlugin.getIconIdForRecordingFile(file);
				if (iconId == -1) {
					iconId = R.drawable.ic_action_photo_dark;
				}
				setupIcon(icon, iconId, itemSelected);
				break;
			case TRACKS:
				String fileName = ((File) currentItem).getName();
				title.setText(GpxUiHelper.getGpxTitle(fileName));
				setupIcon(icon, R.drawable.ic_action_route_distance, itemSelected);
				break;
			case GLOBAL:
				String name = ((GlobalSettingsItem) currentItem).getPublicName(app);
				title.setText(name);
				setupIcon(icon, R.drawable.ic_action_settings, itemSelected);
				break;
			case OSM_NOTES:
				title.setText(((OsmNotesPoint) currentItem).getText());
				setupIcon(icon, R.drawable.ic_action_osm_note_add, itemSelected);
				break;
			case OSM_EDITS:
				title.setText(OsmEditingPlugin.getTitle((OpenstreetmapPoint) currentItem, app));
				setupIcon(icon, R.drawable.ic_action_info_dark, itemSelected);
				break;
			case OFFLINE_MAPS:
				long size;
				if (currentItem instanceof FileSettingsItem) {
					FileSettingsItem currentFileItem = (FileSettingsItem) currentItem;
					file = currentFileItem.getFile();
					size = currentFileItem.getSize();
				} else {
					file = (File) currentItem;
					size = file.length();
				}
				title.setText(FileNameTranslationHelper.getFileName(app,
						app.getResourceManager().getOsmandRegions(),
						file.getName()));
				FileSubtype subtype = FileSubtype.getSubtypeByPath(app, file.getPath());
				switch (subtype) {
					case SRTM_MAP:
						iconId = R.drawable.ic_plugin_srtm;
						break;
					case WIKI_MAP:
						iconId = R.drawable.ic_plugin_wikipedia;
						break;
					default:
						iconId = R.drawable.ic_map;
				}
				setupIcon(icon, iconId, itemSelected);
				subText.setText(AndroidUtils.formatSize(app, size));
				subText.setVisibility(View.VISIBLE);
				break;
			case FAVORITES:
				FavoriteGroup favoriteGroup = (FavoriteGroup) currentItem;
				title.setText(favoriteGroup.getDisplayName(app));
				setupIcon(icon, R.drawable.ic_action_favorite, itemSelected);
				break;
			case TTS_VOICE:
			case VOICE:
				file = (File) currentItem;
				title.setText(FileNameTranslationHelper.getFileName(app,
						app.getResourceManager().getOsmandRegions(),
						file.getName()));
				setupIcon(icon, R.drawable.ic_action_volume_up, itemSelected);
				break;
			default:
				return child;
		}
		return child;
	}

	@Override
	public int getGroupCount() {
		return itemsTypes.size();
	}

	@Override
	public int getChildrenCount(int i) {
		return itemsMap.get(itemsTypes.get(i)).size();
	}

	@Override
	public Object getGroup(int i) {
		return itemsMap.get(itemsTypes.get(i));
	}

	@Override
	public Object getChild(int groupPosition, int childPosition) {
		return itemsMap.get(itemsTypes.get(groupPosition)).get(childPosition);
	}

	@Override
	public long getGroupId(int i) {
		return i;
	}

	@Override
	public long getChildId(int groupPosition, int childPosition) {
		return groupPosition * 10000 + childPosition;
	}

	@Override
	public boolean hasStableIds() {
		return false;
	}

	@Override
	public boolean isChildSelectable(int i, int i1) {
		return true;
	}

	private String getSelectedItemsAmount(List<?> listItems, ExportSettingsType type) {
		int amount = 0;
		long amountSize = 0;
		for (Object item : listItems) {
			if (data.contains(item)) {
				amount++;
				if (type == OFFLINE_MAPS) {
					if (item instanceof FileSettingsItem) {
						amountSize += ((FileSettingsItem) item).getSize();
					} else {
						amountSize += ((File) item).length();
					}
				}
			}
		}
		String itemsOf = app.getString(R.string.n_items_of_z, String.valueOf(amount), String.valueOf(listItems.size()));
		return amountSize == 0 ? itemsOf : app.getString(R.string.ltr_or_rtl_combine_via_bold_point, itemsOf,
				AndroidUtils.formatSize(app, amountSize));
	}

	private int getGroupTitle(ExportSettingsType type) {
		switch (type) {
			case PROFILE:
				return R.string.shared_string_profiles;
			case QUICK_ACTIONS:
				return R.string.configure_screen_quick_action;
			case POI_TYPES:
				return R.string.poi_dialog_poi_type;
			case MAP_SOURCES:
				return R.string.quick_action_map_source_title;
			case CUSTOM_RENDER_STYLE:
				return R.string.shared_string_rendering_style;
			case CUSTOM_ROUTING:
				return R.string.shared_string_routing;
			case AVOID_ROADS:
				return R.string.avoid_road;
			case TRACKS:
				return R.string.shared_string_tracks;
			case MULTIMEDIA_NOTES:
				return R.string.audionotes_plugin_name;
			case GLOBAL:
				return R.string.general_settings_2;
			case OSM_NOTES:
				return R.string.osm_notes;
			case OSM_EDITS:
				return R.string.osm_edits;
			case OFFLINE_MAPS:
				return R.string.shared_string_maps;
			case FAVORITES:
				return R.string.shared_string_favorites;
			case TTS_VOICE:
				return R.string.local_indexes_cat_tts;
			case VOICE:
				return R.string.local_indexes_cat_voice;
			default:
				return R.string.access_empty_list;
		}
	}

	private void setupIcon(ImageView icon, int iconRes, boolean itemSelected) {
		if (itemSelected) {
			icon.setImageDrawable(uiUtilities.getIcon(iconRes, activeColorRes));
		} else {
			icon.setImageDrawable(uiUtilities.getIcon(iconRes, nightMode));
		}
	}

	public void updateSettingsList(Map<ExportSettingsType, List<?>> itemsMap) {
		this.itemsMap = itemsMap;
		this.itemsTypes = new ArrayList<>(itemsMap.keySet());
		Collections.sort(itemsTypes);
		notifyDataSetChanged();
	}

	public void clearSettingsList() {
		this.itemsMap.clear();
		this.itemsTypes.clear();
		notifyDataSetChanged();
	}

	public void selectAll(boolean selectAll) {
		data.clear();
		if (selectAll) {
			for (List<?> values : itemsMap.values()) {
				data.addAll(values);
			}
		}
		notifyDataSetChanged();
	}

	List<? super Object> getData() {
		return this.data;
	}
}
package net.osmand.plus.onlinerouting;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.plus.R;
import net.osmand.util.Algorithms;

import java.util.HashMap;
import java.util.Map;

public class OnlineRoutingEngine {

	public final static String ONLINE_ROUTING_ENGINE_PREFIX = "online_routing_engine_";

	public enum EngineParameter {
		CUSTOM_NAME,
		CUSTOM_URL,
		API_KEY
	}

	private String stringKey;
	private EngineType type;
	private String vehicleKey;
	private Map<String, String> params = new HashMap<>();

	public OnlineRoutingEngine(@NonNull String stringKey,
	                           @NonNull EngineType type,
	                           @NonNull String vehicleKey,
	                           @Nullable Map<String, String> params) {
		this(stringKey, type, vehicleKey);
		if (!Algorithms.isEmpty(params)) {
			this.params.putAll(params);
		}
	}

	public OnlineRoutingEngine(@NonNull String stringKey,
	                           @NonNull EngineType type,
	                           @NonNull String vehicleKey) {
		this.stringKey = stringKey;
		this.type = type;
		this.vehicleKey = vehicleKey;
	}

	public String getStringKey() {
		return stringKey;
	}

	public EngineType getType() {
		return type;
	}

	public String getBaseUrl() {
		String customUrl = getParameter(EngineParameter.CUSTOM_URL);
		if (Algorithms.isEmpty(customUrl)) {
			return type.getStandardUrl();
		}
		return customUrl;
	}

	public String getVehicleKey() {
		return vehicleKey;
	}

	public Map<String, String> getParams() {
		return params;
	}

	public String getParameter(EngineParameter paramKey) {
		return params.get(paramKey.name());
	}

	public void putParameter(EngineParameter paramKey, String paramValue) {
		params.put(paramKey.name(), paramValue);
	}

	public String getName(@NonNull Context ctx) {
		String customName = getParameter(EngineParameter.CUSTOM_NAME);
		if (customName != null) {
			return customName;
		} else {
			return getStandardName(ctx);
		}
	}

	private String getStandardName(@NonNull Context ctx) {
		return getStandardName(ctx, type, vehicleKey);
	}

	public static String getStandardName(@NonNull Context ctx,
	                                     @NonNull EngineType type,
	                                     @NonNull String vehicleKey) {
		String vehicleTitle = VehicleType.toHumanString(ctx, vehicleKey);
		String pattern = ctx.getString(R.string.ltr_or_rtl_combine_via_dash);
		return String.format(pattern, type.getTitle(), vehicleTitle);
	}

	public static OnlineRoutingEngine createNewEngine(@NonNull EngineType type,
	                                                  @NonNull String vehicleKey,
	                                                  @Nullable Map<String, String> params) {
		return new OnlineRoutingEngine(generateKey(), type, vehicleKey, params);
	}

	private static String generateKey() {
		return ONLINE_ROUTING_ENGINE_PREFIX + System.currentTimeMillis();
	}
}

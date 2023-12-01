package com.applovin.mediation.adapters;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.Nullable;

import com.applovin.mediation.MaxAdFormat;
import com.applovin.mediation.adapter.MaxAdViewAdapter;
import com.applovin.mediation.adapter.MaxAdapterError;
import com.applovin.mediation.adapter.MaxInterstitialAdapter;
import com.applovin.mediation.adapter.MaxNativeAdAdapter;
import com.applovin.mediation.adapter.MaxRewardedAdapter;
import com.applovin.mediation.adapter.listeners.MaxAdViewAdapterListener;
import com.applovin.mediation.adapter.listeners.MaxInterstitialAdapterListener;
import com.applovin.mediation.adapter.listeners.MaxRewardedAdapterListener;
import com.applovin.mediation.adapter.parameters.MaxAdapterInitializationParameters;
import com.applovin.mediation.adapter.parameters.MaxAdapterParameters;
import com.applovin.mediation.adapter.parameters.MaxAdapterResponseParameters;
import com.applovin.sdk.AppLovinSdk;
import com.xy.sdk.Controller;
import com.xy.sdk.Listener;
import com.xy.sdk.Privacy;
import com.xy.sdk.PrivacyStatus;
import com.xy.sdk.SDK;
import com.xy.sdk.Size;
import com.xy.sdk.View;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class AdtalosMediationAdapter
        extends MediationAdapterBase
        implements MaxAdViewAdapter, MaxInterstitialAdapter, MaxRewardedAdapter, MaxNativeAdAdapter {
    private static final AtomicBoolean initialized = new AtomicBoolean();
    private static InitializationStatus status;

    private static Map<String, View> adViews = new ConcurrentHashMap<>();
    private static Map<String, Controller> controllers = new ConcurrentHashMap<>();

    public AdtalosMediationAdapter(final AppLovinSdk sdk) {
        super(sdk);
    }

    @Override
    public void initialize(final MaxAdapterInitializationParameters maxAdapterInitializationParameters,
            final Activity activity, final OnCompletionListener onCompletionListener) {
        log("Initializing Adtalos SDK...");
        if (initialized.compareAndSet(false, true)) {
            Context context = getContext(activity);
            status = InitializationStatus.DOES_NOT_APPLY;
            SDK.init(context);
        }
        onCompletionListener.onCompletion(status, null);
    }

    private Context getContext(@Nullable Activity activity) {
        // NOTE: `activity` can only be null in 11.1.0+, and `getApplicationContext()`
        // is introduced in 11.1.0
        return (activity != null) ? activity.getApplicationContext() : getApplicationContext();
    }

    @Override
    public String getSdkVersion() {
        return SDK.VERSION;
    }

    @Override
    public String getAdapterVersion() {
        return SDK.VERSION + ".0";
    }

    @Override
    public void onDestroy() {
        for (View adview : adViews.values()) {
            if (adview != null) {
                adview.destroy();
            }
        }
        adViews.clear();
        controllers.clear();
    }

    private Privacy getPrivacy(MaxAdapterParameters parameters) {
        Privacy.Builder builder = new Privacy.Builder();
        Boolean hasUserConsent = parameters.hasUserConsent();
        if (hasUserConsent == null) {
            builder.setGDPR(PrivacyStatus.UNKNOWN);
        } else if (hasUserConsent.booleanValue()) {
            builder.setGDPR(PrivacyStatus.AUTHORIZED);
        } else {
            builder.setGDPR(PrivacyStatus.UNAUTHORIZED);
        }
        Boolean isAgeRestrictedUser = parameters.isAgeRestrictedUser();
        if (isAgeRestrictedUser == null) {
            builder.setCOPPA(PrivacyStatus.UNKNOWN);
        } else if (isAgeRestrictedUser.booleanValue()) {
            builder.setCOPPA(PrivacyStatus.UNAUTHORIZED);
        } else {
            builder.setCOPPA(PrivacyStatus.AUTHORIZED);
        }
        Boolean isDoNotSell = parameters.isDoNotSell();
        if (isDoNotSell == null) {
            builder.setCCPA(PrivacyStatus.UNKNOWN);
        } else if (isDoNotSell.booleanValue()) {
            builder.setCCPA(PrivacyStatus.UNAUTHORIZED);
        } else {
            builder.setCCPA(PrivacyStatus.AUTHORIZED);
        }
        return builder.build();
    }

    @Override
    public void loadAdViewAd(final MaxAdapterResponseParameters parameters, final MaxAdFormat maxAdFormat,
            final Activity activity, final MaxAdViewAdapterListener adapterListener) {
        final String placementId = parameters.getThirdPartyAdPlacementId();
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                View adView = adViews.get(placementId);
                if (adView == null) {
                    adView = new View(getContext(activity));
                    if (maxAdFormat == MaxAdFormat.BANNER) {
                        adView.setSize(Size.BANNER);
                    } else {
                        adView.setSize(new Size(maxAdFormat.getSize().getWidth(), maxAdFormat.getSize().getHeight()));
                    }
                    adView.setListener(new AdViewListener(maxAdFormat, placementId, adapterListener));
                    adViews.put(placementId, adView);
                }
                SDK.setPrivacy(getPrivacy(parameters));
                adView.load(placementId, false);
            }
        });
    }

    @Override
    public void loadInterstitialAd(final MaxAdapterResponseParameters parameters, final Activity activity,
            final MaxInterstitialAdapterListener adapterListener) {
        final String placementId = parameters.getThirdPartyAdPlacementId();
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Controller interstitialAd = new Controller(placementId, Controller.INTERSTITIAL);
                interstitialAd.setListener(new InterstitialAdListener(placementId, adapterListener));
                controllers.put(placementId, interstitialAd);
                SDK.setPrivacy(getPrivacy(parameters));
                interstitialAd.load();
            }
        });
    }

    @Override
    public void showInterstitialAd(final MaxAdapterResponseParameters parameters, final Activity activity,
            final MaxInterstitialAdapterListener adapterListener) {
        String placementId = parameters.getThirdPartyAdPlacementId();
        Controller interstitialAd = controllers.get(placementId);
        if (interstitialAd == null) {
            log("Interstitial ad not ready.");
            adapterListener.onInterstitialAdDisplayFailed(MaxAdapterError.AD_DISPLAY_FAILED);
        }
        interstitialAd.show();
    }

    @Override
    public void loadRewardedAd(final MaxAdapterResponseParameters parameters, final Activity activity,
            final MaxRewardedAdapterListener adapterListener) {
        final String placementId = parameters.getThirdPartyAdPlacementId();
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Controller rewardedAd = new Controller(placementId, Controller.SPLASH);
                rewardedAd.setListener(new RewardedAdListener(placementId, adapterListener));
                controllers.put(placementId, rewardedAd);
                SDK.setPrivacy(getPrivacy(parameters));
                rewardedAd.load();
            }
        });
    }

    @Override
    public void showRewardedAd(final MaxAdapterResponseParameters parameters, final Activity activity,
            final MaxRewardedAdapterListener adapterListener) {
        String placementId = parameters.getThirdPartyAdPlacementId();
        Controller rewardedAd = controllers.get(placementId);
        if (rewardedAd == null) {
            log("Rewarded ad not ready.");
            adapterListener.onRewardedAdDisplayFailed(MaxAdapterError.AD_DISPLAY_FAILED);
        }
        rewardedAd.show();
    }

    private static MaxAdapterError toMaxError(Exception e) {
        String message = e.getLocalizedMessage();
        String[] result = message.split(":", 2);
        if (result.length != 2) {
            return MaxAdapterError.NO_CONNECTION;
        }
        try {
            int code = Integer.parseInt(result[0]);
            switch (code) {
                case 204:
                    return MaxAdapterError.NO_FILL;
                case 404:
                    return MaxAdapterError.INVALID_CONFIGURATION;
                case 500:
                    return MaxAdapterError.SERVER_ERROR;
            }
        } catch (Exception ex) {
        }
        return MaxAdapterError.UNSPECIFIED;
    }

    private class AdViewListener implements Listener {
        private final MaxAdFormat maxAdFormat;
        private final String placementId;
        private final MaxAdViewAdapterListener adapterListener;

        public AdViewListener(MaxAdFormat maxAdFormat, String placementId, MaxAdViewAdapterListener adapterListener) {
            this.maxAdFormat = maxAdFormat;
            this.placementId = placementId;
            this.adapterListener = adapterListener;
        }

        @Override
        public void onRendered() {
            log(maxAdFormat.getLabel() + " ad " + placementId + " rendered.");
        }

        @Override
        public void onImpressionFinished() {
            log(maxAdFormat.getLabel() + " ad " + placementId + " impression finished.");
            adapterListener.onAdViewAdDisplayed();
        }

        @Override
        public void onImpressionFailed() {
            log(maxAdFormat.getLabel() + " ad " + placementId + " impression failed.");
            adapterListener.onAdViewAdDisplayFailed(MaxAdapterError.AD_DISPLAY_FAILED);
        }

        @Override
        public void onImpressionReceivedError(int i, String s) {
            log(maxAdFormat.getLabel() + " ad " + placementId + " impression received error: " + s + "(" + i + ").");
        }

        @Override
        public void onLoaded() {
            View adView = adViews.get(placementId);
            if (adView != null) {
                log(maxAdFormat.getLabel() + " ad " + placementId + " loaded.");
                adapterListener.onAdViewAdLoaded(adView);
                adView.show();
            }
        }

        @Override
        public void onFailedToLoad(Exception e) {
            log(maxAdFormat.getLabel() + " ad " + placementId + " failed to load: " + e.getLocalizedMessage() + ".");
            adapterListener.onAdViewAdLoadFailed(toMaxError(e));
        }

        @Override
        public void onOpened() {
            log(maxAdFormat.getLabel() + " ad " + placementId + " opened.");
            adapterListener.onAdViewAdExpanded();
        }

        @Override
        public void onClicked() {
            log(maxAdFormat.getLabel() + " ad " + placementId + " clicked.");
            adapterListener.onAdViewAdClicked();
        }

        @Override
        public void onLeftApplication() {
            log(maxAdFormat.getLabel() + " ad " + placementId + " left application.");
        }

        @Override
        public void onClosed() {
            log(maxAdFormat.getLabel() + " ad " + placementId + " closed.");
            adapterListener.onAdViewAdCollapsed();
        }
    }

    private class InterstitialAdListener implements Listener {
        private final String placementId;
        private final MaxInterstitialAdapterListener adapterListener;

        public InterstitialAdListener(String placementId, MaxInterstitialAdapterListener adapterListener) {
            this.placementId = placementId;
            this.adapterListener = adapterListener;
        }

        public void onRendered() {
            log("interstitial ad " + placementId + " rendered.");
        }

        @Override
        public void onImpressionFinished() {
            log("interstitial ad " + placementId + " impression finished.");
            adapterListener.onInterstitialAdDisplayed();
        }

        @Override
        public void onImpressionFailed() {
            log("interstitial ad " + placementId + " impression failed.");
            adapterListener.onInterstitialAdDisplayFailed(MaxAdapterError.AD_DISPLAY_FAILED);
        }

        @Override
        public void onImpressionReceivedError(int i, String s) {
            log("interstitial ad " + placementId + " impression received error: " + s + "(" + i + ").");
        }

        @Override
        public void onLoaded() {
            log("interstitial ad " + placementId + " loaded.");
            adapterListener.onInterstitialAdLoaded();
        }

        @Override
        public void onFailedToLoad(Exception e) {
            log("interstitial ad " + placementId + " failed to load: " + e.getLocalizedMessage() + ".");
            adapterListener.onInterstitialAdLoadFailed(toMaxError(e));
        }

        @Override
        public void onOpened() {
            log("interstitial ad " + placementId + " opened.");
        }

        @Override
        public void onClicked() {
            log("interstitial ad " + placementId + " clicked.");
            adapterListener.onInterstitialAdClicked();
        }

        @Override
        public void onLeftApplication() {
            log("interstitial ad " + placementId + " left application.");
        }

        @Override
        public void onClosed() {
            log("interstitial ad " + placementId + " closed.");
            adapterListener.onInterstitialAdHidden();
        }
    }

    private class RewardedAdListener implements Listener {
        private final String placementId;
        private final MaxRewardedAdapterListener adapterListener;

        public RewardedAdListener(String placementId, MaxRewardedAdapterListener adapterListener) {
            this.placementId = placementId;
            this.adapterListener = adapterListener;
        }

        public void onRendered() {
            log("rewarded ad " + placementId + " rendered.");
        }

        @Override
        public void onImpressionFinished() {
            log("rewarded ad " + placementId + " impression finished.");
            adapterListener.onRewardedAdDisplayed();
        }

        @Override
        public void onImpressionFailed() {
            log("rewarded ad " + placementId + " impression failed.");
            adapterListener.onRewardedAdDisplayFailed(MaxAdapterError.AD_DISPLAY_FAILED);
        }

        @Override
        public void onImpressionReceivedError(int i, String s) {
            log("rewarded ad " + placementId + " impression received error: " + s + "(" + i + ").");
        }

        @Override
        public void onLoaded() {
            log("rewarded ad " + placementId + " loaded.");
            adapterListener.onRewardedAdLoaded();
        }

        @Override
        public void onFailedToLoad(Exception e) {
            log("rewarded ad " + placementId + " failed to load: " + e.getLocalizedMessage() + ".");
            adapterListener.onRewardedAdLoadFailed(toMaxError(e));
        }

        @Override
        public void onOpened() {
            log("rewarded ad " + placementId + " opened.");
        }

        @Override
        public void onClicked() {
            log("rewarded ad " + placementId + " clicked.");
            adapterListener.onRewardedAdClicked();
        }

        @Override
        public void onLeftApplication() {
            log("rewarded ad " + placementId + " left application.");
        }

        @Override
        public void onClosed() {
            log("rewarded ad " + placementId + " closed.");
            adapterListener.onRewardedAdHidden();
        }
    }
}

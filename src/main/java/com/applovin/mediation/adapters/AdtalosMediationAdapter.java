package com.applovin.mediation.adapters;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.Nullable;

import com.applovin.enterprise.apps.demoapp.BuildConfig;
import com.applovin.mediation.MaxAdFormat;
import com.applovin.mediation.adapter.MaxAdViewAdapter;
import com.applovin.mediation.adapter.MaxAdapterError;
import com.applovin.mediation.adapter.MaxAppOpenAdapter;
import com.applovin.mediation.adapter.MaxInterstitialAdapter;
import com.applovin.mediation.adapter.MaxNativeAdAdapter;
import com.applovin.mediation.adapter.MaxRewardedAdapter;
import com.applovin.mediation.adapter.listeners.MaxAdViewAdapterListener;
import com.applovin.mediation.adapter.listeners.MaxAppOpenAdapterListener;
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
        implements MaxAdViewAdapter, MaxInterstitialAdapter, MaxRewardedAdapter, MaxNativeAdAdapter, MaxAppOpenAdapter {
    private static final String BANNER_TEST_AD_ID = "209A03F87BA3B4EB82BEC9E5F8B41383";
    private static final String APP_OPEN_TEST_AD_ID = "5C3DD65A809B08A2D6CF3DEFBC7E09C7";
    private static final String INTERSTITIAL_TEST_AD_ID = "C28BE5F062F476CC0C73C4F0ED333A72";
    private static final String REWARDED_TEST_AD_ID = "527E187C5DEA600C35309759469ADAA8";
    private static final AtomicBoolean initialized = new AtomicBoolean();
    private static InitializationStatus status;

    private static Map<String, View> adViews = new ConcurrentHashMap<>();
    private static Map<String, Controller> controllers = new ConcurrentHashMap<>();

    public AdtalosMediationAdapter(final AppLovinSdk sdk) {
        super(sdk);
    }

    @Override
    public void initialize(MaxAdapterInitializationParameters maxAdapterInitializationParameters, Activity activity, OnCompletionListener onCompletionListener) {
        log("Initializing Adtalos SDK...");
        if (initialized.compareAndSet(false, true)) {
            Context context = getContext(activity);
            status = InitializationStatus.DOES_NOT_APPLY;
            SDK.init(context);
        }
        onCompletionListener.onCompletion(status, null);
    }

    private Context getContext(@Nullable Activity activity) {
        // NOTE: `activity` can only be null in 11.1.0+, and `getApplicationContext()` is introduced in 11.1.0
        return (activity != null) ? activity.getApplicationContext() : getApplicationContext();
    }

    @Override
    public String getSdkVersion() {
        return SDK.VERSION;
    }

    @Override
    public String getAdapterVersion() {
        return BuildConfig.VERSION_NAME;
    }

    public Privacy getPrivacy(MaxAdapterParameters parameters) {
        Privacy.Builder builder = new Privacy.Builder();
        if (parameters.hasUserConsent()) {
            builder.setGDPR(PrivacyStatus.AUTHORIZED);
        }
        if (!parameters.isAgeRestrictedUser()) {
            builder.setCOPPA(PrivacyStatus.AUTHORIZED);
        }
        if (parameters.isDoNotSell()) {
            builder.setCCPA(PrivacyStatus.UNAUTHORIZED);
        }
        return builder.build();
    }

    @Override
    public void onDestroy() {
        for (View adview: adViews.values()) {
            if (adview != null) {
                adview.destroy();
            }
        }
        adViews.clear();
        controllers.clear();
    }

    @Override
    public void loadAdViewAd(MaxAdapterResponseParameters parameters, MaxAdFormat maxAdFormat, Activity activity, MaxAdViewAdapterListener adapterListener) {
        String placementId = parameters.getThirdPartyAdPlacementId();
        if (parameters.isTesting()) {
            placementId = BANNER_TEST_AD_ID;
        }
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

    @Override
    public void loadInterstitialAd(MaxAdapterResponseParameters parameters, Activity activity, MaxInterstitialAdapterListener adapterListener) {
        String placementId = parameters.getThirdPartyAdPlacementId();
        if (parameters.isTesting()) {
            placementId = INTERSTITIAL_TEST_AD_ID;
        }
        Controller interstitialAd = controllers.get(placementId);
        if (interstitialAd == null) {
            interstitialAd = new Controller(placementId, Controller.INTERSTITIAL);
            interstitialAd.setListener(new InterstitialAdListener(placementId, adapterListener));
            controllers.put(placementId, interstitialAd);
        }
        SDK.setPrivacy(getPrivacy(parameters));
        interstitialAd.load();
    }

    @Override
    public void showInterstitialAd(MaxAdapterResponseParameters parameters, Activity activity, MaxInterstitialAdapterListener adapterListener) {
        String placementId = parameters.getThirdPartyAdPlacementId();
        if (parameters.isTesting()) {
            placementId = INTERSTITIAL_TEST_AD_ID;
        }
        Controller interstitialAd = controllers.get(placementId);
        if (interstitialAd == null) {
            log("Interstitial ad not ready.");
            adapterListener.onInterstitialAdDisplayFailed(MaxAdapterError.AD_DISPLAY_FAILED);
        }
        interstitialAd.show();
    }

    @Override
    public void loadAppOpenAd(MaxAdapterResponseParameters parameters, Activity activity, MaxAppOpenAdapterListener adapterListener) {
        String placementId = parameters.getThirdPartyAdPlacementId();
        if (parameters.isTesting()) {
            placementId = APP_OPEN_TEST_AD_ID;
        }
        Controller splashAd = controllers.get(placementId);
        if (splashAd == null) {
            splashAd = new Controller(placementId, Controller.SPLASH);
            splashAd.setListener(new AppOpenAdListener(placementId, adapterListener));
            controllers.put(placementId, splashAd);
        }
        SDK.setPrivacy(getPrivacy(parameters));
        splashAd.load();
    }

    @Override
    public void showAppOpenAd(MaxAdapterResponseParameters parameters, Activity activity, MaxAppOpenAdapterListener adapterListener) {
        String placementId = parameters.getThirdPartyAdPlacementId();
        if (parameters.isTesting()) {
            placementId = APP_OPEN_TEST_AD_ID;
        }
        Controller splashAd = controllers.get(placementId);
        if (splashAd == null) {
            log("App open ad not ready.");
            adapterListener.onAppOpenAdDisplayFailed(MaxAdapterError.AD_DISPLAY_FAILED);
        }
        splashAd.show();
    }

    @Override
    public void loadRewardedAd(MaxAdapterResponseParameters parameters, Activity activity, MaxRewardedAdapterListener adapterListener) {
        String placementId = parameters.getThirdPartyAdPlacementId();
        if (parameters.isTesting()) {
            placementId = REWARDED_TEST_AD_ID;
        }
        Controller rewardedAd = controllers.get(placementId);
        if (rewardedAd == null) {
            rewardedAd = new Controller(placementId, Controller.SPLASH);
            rewardedAd.setListener(new RewardedAdListener(placementId, adapterListener));
            controllers.put(placementId, rewardedAd);
        }
        SDK.setPrivacy(getPrivacy(parameters));
        rewardedAd.load();
    }

    @Override
    public void showRewardedAd(MaxAdapterResponseParameters parameters, Activity activity, MaxRewardedAdapterListener adapterListener) {
        String placementId = parameters.getThirdPartyAdPlacementId();
        if (parameters.isTesting()) {
            placementId = APP_OPEN_TEST_AD_ID;
        }
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

    private class AppOpenAdListener implements Listener {
        private final String placementId;
        private final MaxAppOpenAdapterListener adapterListener;

        public AppOpenAdListener(String placementId, MaxAppOpenAdapterListener adapterListener) {
            this.placementId = placementId;
            this.adapterListener = adapterListener;
        }

        public void onRendered() {
            log("app open ad " + placementId + " rendered.");
        }

        @Override
        public void onImpressionFinished() {
            log("app open ad " + placementId + " impression finished.");
            adapterListener.onAppOpenAdDisplayed();
        }

        @Override
        public void onImpressionFailed() {
            log("app open ad " + placementId + " impression failed.");
            adapterListener.onAppOpenAdDisplayFailed(MaxAdapterError.AD_DISPLAY_FAILED);
        }

        @Override
        public void onImpressionReceivedError(int i, String s) {
            log("app open ad " + placementId + " impression received error: " + s + "(" + i + ").");
        }

        @Override
        public void onLoaded() {
            log("app open ad " + placementId + " loaded.");
            adapterListener.onAppOpenAdLoaded();
        }

        @Override
        public void onFailedToLoad(Exception e) {
            log("app open ad " + placementId + " failed to load: " + e.getLocalizedMessage() + ".");
            adapterListener.onAppOpenAdLoadFailed(toMaxError(e));
        }

        @Override
        public void onOpened() {
            log("app open ad " + placementId + " opened.");
        }

        @Override
        public void onClicked() {
            log("app open ad " + placementId + " clicked.");
            adapterListener.onAppOpenAdClicked();
        }

        @Override
        public void onLeftApplication() {
            log("app open ad " + placementId + " left application.");
        }

        @Override
        public void onClosed() {
            log("app open ad " + placementId + " closed.");
            adapterListener.onAppOpenAdHidden();
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

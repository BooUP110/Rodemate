package io.booup.roadmate.uiprobe;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.pm.PackageManager;
import android.os.Bundle;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import ai.pleos.playground.navi.data.CurrentLocationInfo;
import ai.pleos.playground.navi.helper.NaviHelper;
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener;
import kotlin.Unit;

/** Use on a parked Pleos AVD. Reports response shape only and never emits coordinates. */
public final class NaviLocationProbe extends Instrumentation {
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        if (getTargetContext().checkSelfPermission("pleos.car.permission.NAVI_ROUTE") != PackageManager.PERMISSION_GRANTED) {
            result.putString("navi_location", "PERMISSION_DENIED");
            finish(Activity.RESULT_CANCELED, result);
            return;
        }
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> outcome = new AtomicReference<>();
        NaviHelper helper = new NaviHelper(getTargetContext());
        NaviHelperEventListener listener = new NaviHelperEventListener() {
            @Override public void onCurrentLocationInfo(CurrentLocationInfo info) {
                Double lat = info == null ? null : info.getLatitude();
                Double lon = info == null ? null : info.getLongitude();
                boolean valid = lat != null && lon != null && Double.isFinite(lat) && Double.isFinite(lon)
                        && Math.abs(lat) <= 90 && Math.abs(lon) <= 180;
                outcome.compareAndSet(null, valid ? "COORDINATES_IN_RANGE" : "NO_VALID_COORDINATES");
                done.countDown();
            }
        };
        try {
            runOnMainSync(() -> {
                helper.initialize(() -> {
                    if (outcome.get() != null) return Unit.INSTANCE;
                    try {
                        helper.addListener(listener);
                        helper.getCurrentLocationInfo();
                    } catch (RuntimeException failure) {
                        outcome.compareAndSet(null, "QUERY_FAILED");
                        done.countDown();
                    }
                    return Unit.INSTANCE;
                }, failure -> {
                    outcome.compareAndSet(null, "INITIALIZE_FAILED");
                    done.countDown();
                    return Unit.INSTANCE;
                });
            });
            done.await(10, TimeUnit.SECONDS);
            outcome.compareAndSet(null, "TIMEOUT");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            outcome.compareAndSet(null, "INTERRUPTED");
        } catch (RuntimeException error) {
            outcome.compareAndSet(null, "SDK_ERROR");
        } finally {
            try {
                runOnMainSync(() -> {
                    try { helper.removeListener(listener); } finally { helper.release(); }
                });
            } catch (RuntimeException error) {
                outcome.set("CLEANUP_FAILED");
            }
        }
        result.putString("navi_location", outcome.get());
        finish("COORDINATES_IN_RANGE".equals(outcome.get()) ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }
}

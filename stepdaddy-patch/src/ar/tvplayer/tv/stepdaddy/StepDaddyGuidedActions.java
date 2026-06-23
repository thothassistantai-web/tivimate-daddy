package ar.tvplayer.tv.stepdaddy;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class StepDaddyGuidedActions {
    private static final String GUIDED_ACTION = "\u076c";
    private static final String GUIDED_ACTION_BUILDER = "\u076c$\u058f";
    private static final String GUIDED_ACTION_BUILDER_BASE = "\u076c$\u0620";
    private static final String GUIDED_ACTION_HANDLER = "\u0620";
    private static final String GUIDED_STEP_FRAGMENT = "androidx.leanback.app.GuidedStepSupportFragment";
    private static final String FIND_MAIN_ACTION_POSITION = "\u0620";
    private static final String FIND_BUTTON_ACTION_POSITION = "\u0780";
    private static final String NOTIFY_ACTION_CHANGED = "\u058f";
    private static final String MAIN_ACTIONS_FIELD = "\u078b";
    private static final String BUTTON_ACTIONS_FIELD = "\u078c";
    private static final String ACTION_DESCRIPTION_FIELD = "\u0781";
    private static final String FIND_ACTION_BY_ID = "\u058f";
    private static final String ACTION_ENABLED = "\u0780";

    static final long ACTION_NEXT = 2L;
    static final long ACTION_DONE = 4L;
    static final long ACTION_ENTER_NAME = 0x64L;
    static final long ACTION_ENTER_URL = 0x64L;

    private static final long DEFAULT_POLL_MS = 2000L;
    private static final long DEFAULT_MAX_WAIT_MS = 180_000L;

    private StepDaddyGuidedActions() {
    }

    static void scheduleAction(Object fragment, long actionId, long delayMs) {
        if (fragment == null) {
            return;
        }
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                invokeAction(fragment, actionId);
            }
        }, delayMs);
    }

    static void scheduleActionWhenReady(
        Object fragment,
        long actionId,
        long initialDelayMs,
        long pollIntervalMs,
        long maxWaitMs
    ) {
        scheduleActionWhenReady(fragment, actionId, initialDelayMs, pollIntervalMs, maxWaitMs, -1);
    }

    static void scheduleActionWhenReady(
        Object fragment,
        long actionId,
        long initialDelayMs,
        long pollIntervalMs,
        long maxWaitMs,
        int minStateOrdinal
    ) {
        if (fragment == null) {
            return;
        }
        Handler handler = new Handler(Looper.getMainLooper());
        final long startedAt = SystemClock.uptimeMillis();
        final long pollMs = pollIntervalMs > 0L ? pollIntervalMs : DEFAULT_POLL_MS;
        final long maxWait = maxWaitMs > 0L ? maxWaitMs : DEFAULT_MAX_WAIT_MS;
        Runnable poller = new Runnable() {
            @Override
            public void run() {
                if (!isFragmentAlive(fragment)) {
                    StepDaddyLog.w("Fragment gone while waiting for action " + actionId);
                    return;
                }
                if (minStateOrdinal >= 0 && getStateOrdinal(fragment) < minStateOrdinal) {
                    if (SystemClock.uptimeMillis() - startedAt < maxWait) {
                        handler.postDelayed(this, pollMs);
                        return;
                    }
                    StepDaddyLog.w("Timed out waiting for fragment state >= " + minStateOrdinal);
                    return;
                }
                Object action = findAction(fragment, actionId);
                if (action == null || !isActionEnabled(action)) {
                    if (SystemClock.uptimeMillis() - startedAt < maxWait) {
                        handler.postDelayed(this, pollMs);
                        return;
                    }
                    StepDaddyLog.w("Timed out waiting for action " + actionId + " to enable");
                    return;
                }
                invokeAction(fragment, actionId);
            }
        };
        handler.postDelayed(poller, initialDelayMs > 0L ? initialDelayMs : pollMs);
    }

    static boolean invokeAction(Object fragment, long actionId) {
        try {
            Object action = createGuidedAction(fragment, actionId);
            if (action == null) {
                return false;
            }
            Class<?> actionClass = Class.forName(GUIDED_ACTION);
            Method onAction = fragment.getClass().getMethod(GUIDED_ACTION_HANDLER, actionClass);
            onAction.invoke(fragment, action);
            StepDaddyLog.i("Guided action " + actionId + " on "
                + fragment.getClass().getSimpleName());
            return true;
        } catch (Exception error) {
            StepDaddyLog.w("Guided action failed id=" + actionId, error);
            return false;
        }
    }

    static boolean setActionDescription(Object fragment, long actionId, String text) {
        if (fragment == null || text == null || text.trim().isEmpty()) {
            return false;
        }
        try {
            Class<?> guided = Class.forName(GUIDED_STEP_FRAGMENT);
            Method findPos = guided.getDeclaredMethod(FIND_MAIN_ACTION_POSITION, long.class);
            findPos.setAccessible(true);
            int position = (Integer) findPos.invoke(fragment, actionId);
            if (position < 0) {
                return false;
            }
            Field actionsField = guided.getDeclaredField(MAIN_ACTIONS_FIELD);
            actionsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.List<Object> actions = (java.util.List<Object>) actionsField.get(fragment);
            Object action = actions.get(position);
            Field description = action.getClass().getField(ACTION_DESCRIPTION_FIELD);
            description.set(action, text.trim());
            Method notifyChanged = guided.getDeclaredMethod(NOTIFY_ACTION_CHANGED, int.class);
            notifyChanged.setAccessible(true);
            notifyChanged.invoke(fragment, position);
            StepDaddyLog.i("Set guided action " + actionId + " text on "
                + fragment.getClass().getSimpleName());
            return true;
        } catch (Exception error) {
            StepDaddyLog.w("Set guided action text failed id=" + actionId, error);
            return false;
        }
    }

    static boolean isActionEnabled(Object fragment, long actionId) {
        Object action = findAction(fragment, actionId);
        return action != null && isActionEnabled(action);
    }

    private static Object findAction(Object fragment, long actionId) {
        try {
            Class<?> guided = Class.forName(GUIDED_STEP_FRAGMENT);
            Method findMain = guided.getMethod(FIND_ACTION_BY_ID, long.class);
            Object action = findMain.invoke(fragment, actionId);
            if (action != null) {
                return action;
            }
            Method findButtonPos = guided.getMethod(FIND_BUTTON_ACTION_POSITION, long.class);
            int position = (Integer) findButtonPos.invoke(fragment, actionId);
            if (position < 0) {
                return null;
            }
            Field buttonActions = guided.getDeclaredField(BUTTON_ACTIONS_FIELD);
            buttonActions.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.List<Object> actions = (java.util.List<Object>) buttonActions.get(fragment);
            if (actions == null || position >= actions.size()) {
                return null;
            }
            return actions.get(position);
        } catch (Exception error) {
            StepDaddyLog.w("Find action failed id=" + actionId, error);
            return null;
        }
    }

    private static boolean isActionEnabled(Object action) {
        try {
            Method enabled = action.getClass().getMethod(ACTION_ENABLED);
            Object result = enabled.invoke(action);
            return !(result instanceof Boolean) || (Boolean) result;
        } catch (Exception error) {
            return true;
        }
    }

    private static boolean isFragmentAlive(Object fragment) {
        try {
            Method isAdded = fragment.getClass().getMethod("isAdded");
            Object added = isAdded.invoke(fragment);
            if (added instanceof Boolean && !(Boolean) added) {
                return false;
            }
            Method getContext = fragment.getClass().getMethod("getContext");
            return getContext.invoke(fragment) != null;
        } catch (Exception error) {
            return false;
        }
    }

    private static int getStateOrdinal(Object fragment) {
        if (fragment == null) {
            return -1;
        }
        String className = fragment.getClass().getName();
        String fieldName;
        if (className.endsWith("PlaylistStatusFragment")) {
            fieldName = "\u0793";
        } else if (className.endsWith("PlaylistTvgUrlFragment")) {
            fieldName = "\u0792";
        } else {
            return -1;
        }
        try {
            Field stateField = fragment.getClass().getDeclaredField(fieldName);
            stateField.setAccessible(true);
            Object state = stateField.get(fragment);
            if (state instanceof Enum) {
                return ((Enum<?>) state).ordinal();
            }
        } catch (Exception error) {
            StepDaddyLog.w("Fragment state read failed for " + className, error);
        }
        return -1;
    }

    private static Object createGuidedAction(Object fragment, long actionId) throws Exception {
        Context context = (Context) fragment.getClass().getMethod("getContext").invoke(fragment);
        Class<?> builderClass = Class.forName(GUIDED_ACTION_BUILDER);
        Constructor<?> ctor = builderClass.getDeclaredConstructor(Context.class);
        ctor.setAccessible(true);
        Object builder = ctor.newInstance(context);
        Class<?> builderBase = Class.forName(GUIDED_ACTION_BUILDER_BASE);
        Field idField = builderBase.getDeclaredField(GUIDED_ACTION_HANDLER);
        idField.setAccessible(true);
        idField.setLong(builder, actionId);
        Method build = builderClass.getDeclaredMethod("\u058f");
        build.setAccessible(true);
        return build.invoke(builder);
    }
}

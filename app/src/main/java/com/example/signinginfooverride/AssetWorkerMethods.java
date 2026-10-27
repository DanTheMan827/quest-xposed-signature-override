package com.example.signinginfooverride;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Kotlin coroutine helpers may be synthetic while still carrying the real task. */
final class AssetWorkerMethods {
    static boolean boundary(Method method, Class<?> request, Class<?> task, Class<?> job, boolean includeReceiver) {
        int flags = method.getModifiers();
        if (Modifier.isAbstract(flags) || Modifier.isNative(flags)) return false;
        if (includeReceiver && !Modifier.isStatic(flags)) return true;
        for (Class<?> argument : method.getParameterTypes()) {
            if (request.isAssignableFrom(argument) || task.isAssignableFrom(argument) || job.isAssignableFrom(argument))
                return true;
        }
        return false;
    }
}

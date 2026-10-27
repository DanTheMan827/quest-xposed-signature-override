package com.example.signinginfooverride;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class AssetWorkerMethodsTest {
    private static final class Request { }
    private static final class Task { void execute() { } }
    private static final class Job { }
    private static Runnable coroutine(Task task) { return () -> task.execute(); }
    private static Runnable unrelated(String value) { return () -> value.length(); }

    public static void main(String[] args) {
        boolean foundTask = false, foundUnrelated = false;
        for (Method method : AssetWorkerMethodsTest.class.getDeclaredMethods()) {
            if (!method.isSynthetic() || !Modifier.isStatic(method.getModifiers())) continue;
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length != 1) continue;
            if (parameters[0] == Task.class) {
                // Reproduces the ACC_STATIC | ACC_SYNTHETIC task boundary found in OCMS.
                if (!AssetWorkerMethods.boundary(method, Request.class, Task.class, Job.class, true))
                    throw new AssertionError("Synthetic task helper was excluded");
                foundTask = true;
            } else if (parameters[0] == String.class) {
                if (AssetWorkerMethods.boundary(method, Request.class, Task.class, Job.class, true))
                    throw new AssertionError("Unrelated synthetic helper was included");
                foundUnrelated = true;
            }
        }
        if (!foundTask || !foundUnrelated) throw new AssertionError("Compiler-generated regression fixtures missing");
        System.out.println("1 synthetic asset worker regression check passed");
    }
}

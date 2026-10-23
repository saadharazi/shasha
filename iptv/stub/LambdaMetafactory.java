package java.lang.invoke;

// android.jar ما فيه هذا الكلاس، وjavac يحتاجه عشان الـ lambda. للبناء بس: d8 يحوّل الـ lambda وما يدخل هذا في التطبيق.
public final class LambdaMetafactory {
    public static CallSite metafactory(MethodHandles.Lookup caller, String name, MethodType type, MethodType sam,
                                       MethodHandle impl, MethodType inst) {
        return null;
    }

    public static CallSite altMetafactory(MethodHandles.Lookup caller, String name, MethodType type, Object... args) {
        return null;
    }
}

package io.github.mkremins.fanciful;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;

public final class ArrayWrapper<E> {

    @SuppressWarnings({"unchecked", "unused"})
    public static <T> T[] toArray(Iterable<? extends T> list, Class<T> c) {
        int size = -1;
        if (list instanceof Collection<?>) {
            Collection coll = (Collection) list;
            size = coll.size();
        }


        if (size < 0) {
            size = 0;
            // Ugly hack: Count it ourselves
            for (T element : list) {
                size++;
            }
        }

        T[] result = (T[]) Array.newInstance(c, size);
        int i = 0;
        for (T element : list) { // Assumes iteration order is consistent
            result[i++] = element; // Assign array element at index THEN increment counter
        }
        return result;
    }

    private E[] array;

    @SafeVarargs
    public ArrayWrapper(E... elements) {
        setArray(elements);
    }

    public E[] getArray() {
        return array;
    }

    public void setArray(E[] array) {
        if (array == null) {
            throw new NullPointerException("array");
        }
        this.array = array;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ArrayWrapper && Arrays.equals(array, ((ArrayWrapper) other).array);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(array);
    }

}

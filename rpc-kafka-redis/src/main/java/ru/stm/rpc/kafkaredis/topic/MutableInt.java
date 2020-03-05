package ru.stm.rpc.kafkaredis.topic;

public class MutableInt extends Number implements Comparable {
    private static final long serialVersionUID = 512176391864L;
    private int value;

    public MutableInt() {
    }

    public MutableInt(int value) {
        this.value = value;
    }

    public MutableInt(Number value) {
        this.value = value.intValue();
    }

    public MutableInt(String value) throws NumberFormatException {
        this.value = Integer.parseInt(value);
    }

    public Integer getValue() {
        return new Integer(this.value);
    }

    public void setValue(int value) {
        this.value = value;
    }

    public void setValue(Object value) {
        this.setValue(((Number)value).intValue());
    }

    public void increment() {
        ++this.value;
    }

    public void decrement() {
        --this.value;
    }

    public void add(int operand) {
        this.value += operand;
    }

    public void add(Number operand) {
        this.value += operand.intValue();
    }

    public void subtract(int operand) {
        this.value -= operand;
    }

    public void subtract(Number operand) {
        this.value -= operand.intValue();
    }

    public int intValue() {
        return this.value;
    }

    public long longValue() {
        return (long)this.value;
    }

    public float floatValue() {
        return (float)this.value;
    }

    public double doubleValue() {
        return (double)this.value;
    }

    public Integer toInteger() {
        return new Integer(this.intValue());
    }

    public boolean equals(Object obj) {
        if (obj instanceof MutableInt) {
            return this.value == ((MutableInt)obj).intValue();
        } else {
            return false;
        }
    }

    public int hashCode() {
        return this.value;
    }

    public int compareTo(Object obj) {
        MutableInt other = (MutableInt)obj;
        int anotherVal = other.value;
        return this.value < anotherVal ? -1 : (this.value == anotherVal ? 0 : 1);
    }

    public String toString() {
        return String.valueOf(this.value);
    }
}

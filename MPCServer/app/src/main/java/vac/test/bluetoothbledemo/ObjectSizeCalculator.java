package vac.test.bluetoothbledemo;


import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;

public class ObjectSizeCalculator {
    public static long calculate(Object obj) {
        try {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            ObjectOutputStream objectStream = new ObjectOutputStream(byteStream);
            objectStream.writeObject(obj);
            objectStream.close();
            return byteStream.size();
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }
}


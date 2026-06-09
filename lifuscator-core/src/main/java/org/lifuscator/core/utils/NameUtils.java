package org.lifuscator.core.utils;

import lombok.experimental.UtilityClass;

import java.util.concurrent.ThreadLocalRandom;

@UtilityClass
public class NameUtils {

    public static String weirdName() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int length = random.nextInt(16) + 16;
        StringBuilder sb = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            switch (random.nextInt(3)) {
                case 0 -> sb.append('I');
                case 1 -> sb.append('l');

                // https://en.wikipedia.org/wiki/CJK_Unified_Ideographs_(Unicode_block)
                case 2 -> sb.append((char) random.nextInt(0x4E00, 0x9FFF + 1));
            }
        }

        return sb.toString();
    }
}

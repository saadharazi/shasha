package io.github.saadharazi.shasha;

import java.nio.charset.StandardCharsets;

/**
 * رمز QR صغير لعنوان الإضافة من الجوال: نص بايتات، تصحيح أخطاء M، الإصدارات 1 إلى 6 (لين ~100 حرف).
 * مبني على الطريقة المعروفة في مكتبة Nayuki، مختصر على اللي نحتاجه.
 */
final class QR {
    private static final int[] EC = {0, 10, 16, 26, 18, 24, 16};
    private static final int[] BLOCKS = {0, 1, 1, 1, 2, 2, 4};

    private final int size;
    private final boolean[][] dark, fixed;

    /** يرجّع المربعات [صف][عمود]، true = أسود، بدون الهامش الأبيض. */
    static boolean[][] encode(String text) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        int ver = 1;
        while (ver <= 6 && dataCodewords(ver) < 2 + data.length) ver++;
        if (ver > 6) throw new IllegalArgumentException("text too long for QR");
        return new QR(ver, data).dark;
    }

    private QR(int ver, byte[] data) {
        size = ver * 4 + 17;
        dark = new boolean[size][size];
        fixed = new boolean[size][size];
        for (int i = 0; i < size; i++) {
            set(6, i, i % 2 == 0);
            set(i, 6, i % 2 == 0);
        }
        finder(3, 3);
        finder(size - 4, 3);
        finder(3, size - 4);
        if (ver > 1) align(size - 7, size - 7);
        format(0);
        place(codewords(ver, data));

        int best = 0;
        long min = Long.MAX_VALUE;
        for (int m = 0; m < 8; m++) {
            mask(m);
            format(m);
            long p = penalty();
            if (p < min) {
                min = p;
                best = m;
            }
            mask(m);
        }
        mask(best);
        format(best);
    }

    private static int dataCodewords(int ver) {
        int raw = ((16 * ver + 128) * ver + 64 - (ver > 1 ? 25 : 0)) / 8;
        return raw - EC[ver] * BLOCKS[ver];
    }

    /** البيانات (وضع البايت) مع البايتات الإضافية، مقسومة على البلوكات ومعها التصحيح، ومتداخلة. */
    private static byte[] codewords(int ver, byte[] data) {
        int cap = dataCodewords(ver);
        byte[] bytes = new byte[cap];
        int bit = 0;
        bit = put(bytes, bit, 0b0100, 4);
        bit = put(bytes, bit, data.length, 8);
        for (byte b : data) bit = put(bytes, bit, b & 0xFF, 8);
        bit = put(bytes, bit, 0, Math.min(4, cap * 8 - bit));
        for (int i = (bit + 7) / 8, pad = 0xEC; i < cap; i++, pad ^= 0xEC ^ 0x11) bytes[i] = (byte) pad;

        int raw = ((16 * ver + 128) * ver + 64 - (ver > 1 ? 25 : 0)) / 8;
        int n = BLOCKS[ver], ec = EC[ver], shortCount = n - raw % n, shortLen = raw / n;
        byte[] gen = divisor(ec);
        byte[][] blocks = new byte[n][];
        for (int i = 0, k = 0; i < n; i++) {
            int len = shortLen - ec + (i < shortCount ? 0 : 1);
            byte[] dat = new byte[len];
            System.arraycopy(bytes, k, dat, 0, len);
            k += len;
            byte[] block = new byte[shortLen + 1];
            System.arraycopy(dat, 0, block, 0, len);
            System.arraycopy(remainder(dat, gen), 0, block, block.length - ec, ec);
            blocks[i] = block;
        }
        byte[] out = new byte[raw];
        for (int i = 0, k = 0; i < shortLen + 1; i++)
            for (int j = 0; j < n; j++)
                if (i != shortLen - ec || j >= shortCount) out[k++] = blocks[j][i];
        return out;
    }

    private static int put(byte[] buf, int bit, int value, int len) {
        for (int i = len - 1; i >= 0; i--, bit++)
            if (((value >>> i) & 1) != 0) buf[bit >>> 3] |= (byte) (0x80 >>> (bit & 7));
        return bit;
    }

    private static byte[] divisor(int degree) {
        byte[] r = new byte[degree];
        r[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < degree; j++) {
                r[j] = (byte) mul(r[j] & 0xFF, root);
                if (j + 1 < degree) r[j] ^= r[j + 1];
            }
            root = mul(root, 2);
        }
        return r;
    }

    private static byte[] remainder(byte[] data, byte[] gen) {
        byte[] r = new byte[gen.length];
        for (byte b : data) {
            int f = (b ^ r[0]) & 0xFF;
            System.arraycopy(r, 1, r, 0, r.length - 1);
            r[r.length - 1] = 0;
            for (int i = 0; i < r.length; i++) r[i] ^= (byte) mul(gen[i] & 0xFF, f);
        }
        return r;
    }

    private static int mul(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = (z << 1) ^ ((z >>> 7) * 0x11D);
            z ^= ((y >>> i) & 1) * x;
        }
        return z;
    }

    private void set(int x, int y, boolean on) {
        dark[y][x] = on;
        fixed[y][x] = true;
    }

    private void finder(int cx, int cy) {
        for (int dy = -4; dy <= 4; dy++)
            for (int dx = -4; dx <= 4; dx++) {
                int x = cx + dx, y = cy + dy, d = Math.max(Math.abs(dx), Math.abs(dy));
                if (x >= 0 && x < size && y >= 0 && y < size) set(x, y, d != 2 && d != 4);
            }
    }

    private void align(int cx, int cy) {
        for (int dy = -2; dy <= 2; dy++)
            for (int dx = -2; dx <= 2; dx++) set(cx + dx, cy + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
    }

    /** معلومات التنسيق (مستوى M والقناع) بنسختيها، والمربع الأسود الثابت. */
    private void format(int mask) {
        int data = mask, rem = data;
        for (int i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        int bits = (data << 10 | rem) ^ 0x5412;
        for (int i = 0; i <= 5; i++) set(8, i, bit(bits, i));
        set(8, 7, bit(bits, 6));
        set(8, 8, bit(bits, 7));
        set(7, 8, bit(bits, 8));
        for (int i = 9; i < 15; i++) set(14 - i, 8, bit(bits, i));
        for (int i = 0; i < 8; i++) set(size - 1 - i, 8, bit(bits, i));
        for (int i = 8; i < 15; i++) set(8, size - 15 + i, bit(bits, i));
        set(8, size - 8, true);
    }

    private static boolean bit(int x, int i) {
        return ((x >>> i) & 1) != 0;
    }

    private void place(byte[] data) {
        int i = 0;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) right = 5;
            for (int vert = 0; vert < size; vert++)
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    int y = ((right + 1) & 2) == 0 ? size - 1 - vert : vert;
                    if (!fixed[y][x] && i < data.length * 8) {
                        dark[y][x] = bit(data[i >>> 3], 7 - (i & 7));
                        i++;
                    }
                }
        }
    }

    private void mask(int m) {
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) {
                if (fixed[y][x]) continue;
                boolean flip;
                switch (m) {
                    case 0: flip = (x + y) % 2 == 0; break;
                    case 1: flip = y % 2 == 0; break;
                    case 2: flip = x % 3 == 0; break;
                    case 3: flip = (x + y) % 3 == 0; break;
                    case 4: flip = (x / 3 + y / 2) % 2 == 0; break;
                    case 5: flip = x * y % 2 + x * y % 3 == 0; break;
                    case 6: flip = (x * y % 2 + x * y % 3) % 2 == 0; break;
                    default: flip = ((x + y) % 2 + x * y % 3) % 2 == 0;
                }
                if (flip) dark[y][x] = !dark[y][x];
            }
    }

    // shortcut: no finder-lookalike rule (rule 3) in the score, it only picks a slightly worse mask sometimes; any mask scans.
    private long penalty() {
        long p = 0;
        int black = 0;
        for (int a = 0; a < size; a++) {
            int runRow = 1, runCol = 1;
            for (int b = 0; b < size; b++) {
                if (dark[a][b]) black++;
                if (b == 0) continue;
                runRow = dark[a][b] == dark[a][b - 1] ? runRow + 1 : 1;
                runCol = dark[b][a] == dark[b - 1][a] ? runCol + 1 : 1;
                if (runRow == 5) p += 3; else if (runRow > 5) p++;
                if (runCol == 5) p += 3; else if (runCol > 5) p++;
                if (a > 0 && dark[a][b] == dark[a][b - 1] && dark[a][b] == dark[a - 1][b] && dark[a][b] == dark[a - 1][b - 1]) p += 3;
            }
        }
        int total = size * size;
        p += (long) ((Math.abs(black * 20L - total * 10L) + total - 1) / total - 1) * 10;
        return p;
    }
}

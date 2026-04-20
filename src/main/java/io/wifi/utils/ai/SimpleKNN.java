package io.wifi.utils.ai;

import java.util.*;

/**
 * 极简 KNN (K-Nearest Neighbors) 分类器
 * 适用于低像素手绘图识别（数字矩阵输入）
 * 支持最低匹配度阈值，当最高类别得票比例不足时返回 -1（未知）
 */
public class SimpleKNN {

    // 存储训练样本
    private static class Sample {
        double[] features;  // 特征向量（展平后的矩阵）
        int label;          // 类别标签，如 0=圆形, 1=方形
    }

    private final List<Sample> samples = new ArrayList<>();
    private final int k;  // KNN 的 K 值，通常取奇数，如 3, 5

    /**
     * 构造 KNN 分类器
     * @param k 邻居个数，推荐 3 或 5
     */
    public SimpleKNN(int k) {
        if (k <= 0) throw new IllegalArgumentException("k must be positive");
        this.k = k;
    }

    /**
     * 添加一个训练样本
     * @param features 特征向量（double数组）
     * @param label    类别标签
     */
    public void addSample(double[] features, int label) {
        Sample s = new Sample();
        s.features = features.clone(); // 防御性拷贝
        s.label = label;
        samples.add(s);
    }

    /**
     * 预测样本类别（无阈值，总是返回一个标签）
     * @param features 特征向量
     * @return 预测的类别标签
     */
    public int predict(double[] features) {
        if (samples.isEmpty()) {
            throw new IllegalStateException("No training samples added");
        }

        // 1. 计算与所有样本的欧氏距离，取前 k 个最近的邻居
        PriorityQueue<Neighbor> heap = new PriorityQueue<>(Comparator.comparingDouble(n -> n.distance));
        for (Sample s : samples) {
            double dist = euclideanDistance(features, s.features);
            heap.offer(new Neighbor(dist, s.label));
        }

        // 2. 统计前 k 个邻居的标签投票
        Map<Integer, Integer> labelCount = new HashMap<>();
        for (int i = 0; i < k && !heap.isEmpty(); i++) {
            Neighbor neighbor = heap.poll();
            labelCount.put(neighbor.label, labelCount.getOrDefault(neighbor.label, 0) + 1);
        }

        // 3. 返回得票最高的标签
        int bestLabel = -1;
        int bestCount = -1;
        for (Map.Entry<Integer, Integer> entry : labelCount.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestCount = entry.getValue();
                bestLabel = entry.getKey();
            }
        }
        return bestLabel;
    }

    /**
     * 预测样本类别，带最低匹配度要求（基于投票比例）
     * @param features      特征向量
     * @param minMatchRatio 最低匹配度，范围 (0,1]，例如 0.6 表示最高得票数至少占 K 的 60%
     * @return 预测的类别标签，如果匹配度不足则返回 -1
     */
    public int predictWithThreshold(double[] features, double minMatchRatio) {
        if (samples.isEmpty()) {
            throw new IllegalStateException("No training samples added");
        }
        if (minMatchRatio <= 0 || minMatchRatio > 1) {
            throw new IllegalArgumentException("minMatchRatio must be in (0,1]");
        }

        // 1. 计算距离，取前 k 个最近邻居
        PriorityQueue<Neighbor> heap = new PriorityQueue<>(Comparator.comparingDouble(n -> n.distance));
        for (Sample s : samples) {
            double dist = euclideanDistance(features, s.features);
            heap.offer(new Neighbor(dist, s.label));
        }

        // 2. 投票统计
        Map<Integer, Integer> labelCount = new HashMap<>();
        for (int i = 0; i < k && !heap.isEmpty(); i++) {
            Neighbor neighbor = heap.poll();
            labelCount.put(neighbor.label, labelCount.getOrDefault(neighbor.label, 0) + 1);
        }

        // 3. 找出最高得票数
        int maxVotes = 0;
        int bestLabel = -1;
        for (Map.Entry<Integer, Integer> entry : labelCount.entrySet()) {
            if (entry.getValue() > maxVotes) {
                maxVotes = entry.getValue();
                bestLabel = entry.getKey();
            }
        }

        // 4. 检查匹配度
        double confidence = (double) maxVotes / k;
        if (confidence >= minMatchRatio) {
            return bestLabel;
        } else {
            return -1;   // 未达到最低匹配度，拒绝识别
        }
    }

    /**
     * 预测样本类别，基于最近邻居的绝对距离阈值（可选）
     * @param features    特征向量
     * @param maxDistance 允许的最大欧氏距离（需根据特征维度和数值范围调参）
     * @return 预测类别，如果最近距离 > maxDistance 则返回 -1
     */
    public int predictWithDistanceLimit(double[] features, double maxDistance) {
        if (samples.isEmpty()) return -1;

        double minDist = Double.MAX_VALUE;
        int bestLabel = -1;

        for (Sample s : samples) {
            double dist = euclideanDistance(features, s.features);
            if (dist < minDist) {
                minDist = dist;
                bestLabel = s.label;
            }
        }

        if (minDist <= maxDistance) {
            return bestLabel;
        } else {
            return -1;
        }
    }

    // 辅助类：存储邻居的距离和标签
    private static class Neighbor {
        double distance;
        int label;
        Neighbor(double distance, int label) {
            this.distance = distance;
            this.label = label;
        }
    }

    // 计算两个特征向量的欧氏距离
    private double euclideanDistance(double[] a, double[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Feature vectors must have same length");
        }
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            double diff = a[i] - b[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }

    // ========== 便捷工具：将字节矩阵转为 double 特征向量 ==========
    /**
     * 将 n x m 的字节矩阵转换为 double 特征向量（展平，值归一化到 0~1）
     * @param matrix 输入矩阵，每个元素为 byte（例如 0~255 的灰度值）
     * @return 归一化后的 double 数组
     */
    public static double[] matrixToFeature(byte[][] matrix) {
        if (matrix == null || matrix.length == 0) return new double[0];
        int rows = matrix.length;
        int cols = matrix[0].length;
        double[] feature = new double[rows * cols];
        int idx = 0;
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                int val = matrix[i][j] & 0xFF;   // 转为 0~255 无符号
                feature[idx++] = val / 255.0;     // 归一化到 [0,1]
            }
        }
        return feature;
    }

    /**
     * 将字节矩阵转为原始整数值特征向量（不归一化）
     */
    public static double[] matrixToFeatureRaw(byte[][] matrix) {
        if (matrix == null || matrix.length == 0) return new double[0];
        int rows = matrix.length;
        int cols = matrix[0].length;
        double[] feature = new double[rows * cols];
        int idx = 0;
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                feature[idx++] = matrix[i][j] & 0xFF;
            }
        }
        return feature;
    }

    // ========== 示例主函数 ==========
    public static void main(String[] args) {
        // 假设我们有 8x8 的低像素手绘图（数字矩阵）
        // 类别：0 代表圆形，1 代表方形（举例）

        SimpleKNN knn = new SimpleKNN(5);   // K=5，使用投票比例阈值时更稳定

        // ---- 训练数据：手工构造几个简单样本 ----
        // 样本1: 近似圆形的 8x8 矩阵
        byte[][] circle = {
            {0,0,0,0,0,0,0,0},
            {0,1,1,1,1,1,1,0},
            {0,1,2,2,2,2,1,0},
            {0,1,2,3,3,2,1,0},
            {0,1,2,3,3,2,1,0},
            {0,1,2,2,2,2,1,0},
            {0,1,1,1,1,1,1,0},
            {0,0,0,0,0,0,0,0}
        };
        knn.addSample(matrixToFeature(circle), 0); // 类别0: 圆形

        // 样本2: 近似方形的 8x8 矩阵
        byte[][] square = {
            {0,0,0,0,0,0,0,0},
            {0,3,3,3,3,3,3,0},
            {0,3,1,1,1,1,3,0},
            {0,3,1,1,1,1,3,0},
            {0,3,1,1,1,1,3,0},
            {0,3,1,1,1,1,3,0},
            {0,3,3,3,3,3,3,0},
            {0,0,0,0,0,0,0,0}
        };
        knn.addSample(matrixToFeature(square), 1); // 类别1: 方形

        // 添加更多样本，增加鲁棒性
        byte[][] circle2 = {
            {0,0,0,0,0,0,0,0},
            {0,1,1,1,1,1,0,0},
            {0,1,2,2,2,1,0,0},
            {0,1,2,3,2,1,0,0},
            {0,1,2,2,2,1,0,0},
            {0,1,1,1,1,1,0,0},
            {0,0,0,0,0,0,0,0},
            {0,0,0,0,0,0,0,0}
        };
        knn.addSample(matrixToFeature(circle2), 0);

        byte[][] square2 = {
            {0,0,0,0,0,0,0,0},
            {0,2,2,2,2,2,2,0},
            {0,2,1,1,1,1,2,0},
            {0,2,1,1,1,1,2,0},
            {0,2,1,1,1,1,2,0},
            {0,2,2,2,2,2,2,0},
            {0,0,0,0,0,0,0,0},
            {0,0,0,0,0,0,0,0}
        };
        knn.addSample(matrixToFeature(square2), 1);

        // ---- 测试1: 一个接近圆形的未知图形 ----
        byte[][] unknownCircle = {
            {0,0,0,0,0,0,0,0},
            {0,1,1,1,1,1,1,0},
            {0,1,2,2,2,2,1,0},
            {0,1,2,2,2,2,1,0},
            {0,1,2,2,2,2,1,0},
            {0,1,2,2,2,2,1,0},
            {0,1,1,1,1,1,1,0},
            {0,0,0,0,0,0,0,0}
        };
        double[] feat = matrixToFeature(unknownCircle);
        
        // 无阈值预测
        int normalResult = knn.predict(feat);
        System.out.println("无阈值预测结果: " + normalResult + (normalResult == 0 ? " (圆形)" : " (方形)"));
        
        // 带阈值预测，要求至少 60% 的邻居投票一致
        int thresholdResult = knn.predictWithThreshold(feat, 0.6);
        if (thresholdResult == -1) {
            System.out.println("带阈值(0.6)预测结果: 无法确定，匹配度不足");
        } else {
            System.out.println("带阈值(0.6)预测结果: " + thresholdResult + (thresholdResult == 0 ? " (圆形)" : " (方形)"));
        }

        // ---- 测试2: 一个完全不像训练数据的随机矩阵 ----
        byte[][] noise = new byte[8][8];
        Random rand = new Random();
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                noise[i][j] = (byte) rand.nextInt(256);
            }
        }
        double[] noiseFeat = matrixToFeature(noise);
        int noiseResult = knn.predictWithThreshold(noiseFeat, 0.6);
        System.out.println("\n随机噪声预测结果: " + (noiseResult == -1 ? "无法识别" : "类别 " + noiseResult));
    }
}
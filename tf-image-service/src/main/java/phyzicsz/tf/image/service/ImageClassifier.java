/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package phyzicsz.tf.image.service;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.TextFormat;
import com.typesafe.config.Config;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.tensorflow.SavedModelBundle;
import org.tensorflow.Tensor;
import org.tensorflow.framework.MetaGraphDef;
import org.tensorflow.framework.SignatureDef;
import org.tensorflow.framework.TensorInfo;
import org.tensorflow.types.UInt8;
import phyzicsz.tf.image.service.api.ImageDetectionRequest;
import phyzicsz.tf.image.service.protos.StringIntLabelMapOuterClass.StringIntLabelMap;
import phyzicsz.tf.image.service.protos.StringIntLabelMapOuterClass.StringIntLabelMapItem;

/**
 *
 * @author pborawski
 */
public class ImageClassifier {

    private String[] labels;
    private SavedModelBundle model;
    private final Config config;
    
    public ImageClassifier(final Config config){
        this.config = config;
    }


    public ImageClassifier loadModel() throws IOException {
        String modelPath = config.getString("image-classifier.model");
        String labelPath = config.getString("image-classifier.labels");
//        modelPath = new File(modelPath).getAbsolutePath();
//        labelPath = new File(labelPath).getAbsolutePath();
        
        labels = loadLabels(labelPath);
        model = SavedModelBundle.load(modelPath, "serve");
        printSignature(model);
        return this;
    }

    public void classify(final ImageDetectionRequest request) throws IOException {

        List<Tensor<?>> outputs = null;
        try (Tensor<UInt8> input = makeImageTensor(request)) {
            outputs
                    = model
                            .session()
                            .runner()
                            .feed("image_tensor", input)
                            .fetch("detection_scores")
                            .fetch("detection_classes")
                            .fetch("detection_boxes")
                            .run();
        }
        try (Tensor<Float> scoresT = outputs.get(0).expect(Float.class);
                Tensor<Float> classesT = outputs.get(1).expect(Float.class);
                Tensor<Float> boxesT = outputs.get(2).expect(Float.class)) {
            // All these tensors have:
            // - 1 as the first dimension
            // - maxObjects as the second dimension
            // While boxesT will have 4 as the third dimension (2 sets of (x, y) coordinates).
            // This can be verified by looking at scoresT.shape() etc.
            int maxObjects = (int) scoresT.shape()[1];
            float[] scores = scoresT.copyTo(new float[1][maxObjects])[0];
            float[] classes = classesT.copyTo(new float[1][maxObjects])[0];
            float[][] boxes = boxesT.copyTo(new float[1][maxObjects][4])[0];
            // Print all objects whose score is at least 0.5.
            System.out.printf("* %s\n", request.getName());
            boolean foundSomething = false;
            for (int i = 0; i < scores.length; ++i) {
                if (scores[i] < 0.5) {
                    continue;
                }
                foundSomething = true;
                System.out.printf("\tFound %-20s (score: %.4f)\n", labels[(int) classes[i]], scores[i]);
            }
            if (!foundSomething) {
                System.out.println("No objects detected with a high enough score.");
            }
        }
    }

    private static String[] loadLabels(String filename) throws IOException {
        File file = new File(filename);
        System.out.println("Loading File: " + file);
         System.out.println("File Exists: " + file.exists());
        String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        StringIntLabelMap.Builder builder = StringIntLabelMap.newBuilder();
        TextFormat.merge(text, builder);
        StringIntLabelMap proto = builder.build();
        int maxId = 0;
        for (StringIntLabelMapItem item : proto.getItemList()) {
            if (item.getId() > maxId) {
                maxId = item.getId();
            }
        }
        String[] ret = new String[maxId + 1];
        for (StringIntLabelMapItem item : proto.getItemList()) {
            ret[item.getId()] = item.getDisplayName();
        }
        return ret;
    }

    private static void printSignature(SavedModelBundle model) throws InvalidProtocolBufferException {
        MetaGraphDef m = MetaGraphDef.parseFrom(model.metaGraphDef());
        SignatureDef sig = m.getSignatureDefOrThrow("serving_default");
        int numInputs = sig.getInputsCount();
        int i = 1;
        System.out.println("MODEL SIGNATURE");
        System.out.println("Inputs:");
        for (Map.Entry<String, TensorInfo> entry : sig.getInputsMap().entrySet()) {
            TensorInfo t = entry.getValue();
            System.out.printf(
                    "%d of %d: %-20s (Node name in graph: %-20s, type: %s)\n",
                    i++, numInputs, entry.getKey(), t.getName(), t.getDtype());
        }
        int numOutputs = sig.getOutputsCount();
        i = 1;
        System.out.println("Outputs:");
        for (Map.Entry<String, TensorInfo> entry : sig.getOutputsMap().entrySet()) {
            TensorInfo t = entry.getValue();
            System.out.printf(
                    "%d of %d: %-20s (Node name in graph: %-20s, type: %s)\n",
                    i++, numOutputs, entry.getKey(), t.getName(), t.getDtype());
        }
        System.out.println("-----------------------------------------------");
    }

    private static Tensor<UInt8> makeImageTensor(final ImageDetectionRequest request) throws IOException {
        InputStream in = new ByteArrayInputStream(request.getBytes());
        BufferedImage img = ImageIO.read(in);
        if (img.getType() != BufferedImage.TYPE_3BYTE_BGR) {
            throw new IOException(
                    String.format(
                            "Expected 3-byte BGR encoding in BufferedImage, found %d (file: %s). This code could be made more robust",
                            img.getType(), request.getName()));
        }
        byte[] data = ((DataBufferByte) img.getData().getDataBuffer()).getData();
        // ImageIO.read seems to produce BGR-encoded images, but the model expects RGB.
        bgr2rgb(data);
        final long BATCH_SIZE = 1;
        final long CHANNELS = 3;
        long[] shape = new long[]{BATCH_SIZE, img.getHeight(), img.getWidth(), CHANNELS};
        return Tensor.create(UInt8.class, shape, ByteBuffer.wrap(data));
    }

    private static void bgr2rgb(byte[] data) {
        for (int i = 0; i < data.length; i += 3) {
            byte tmp = data[i];
            data[i] = data[i + 2];
            data[i + 2] = tmp;
        }
    }
}

/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2011-2019 Broad Institute, Aiden Lab
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */

package juicebox.tools.utils.original.norm;

import juicebox.HiC;
import juicebox.HiCGlobals;
import juicebox.data.*;
import juicebox.gui.SuperAdapter;
import juicebox.tools.utils.original.ExpectedValueCalculation;
import juicebox.windowui.HiCZoom;
import juicebox.windowui.NormalizationHandler;
import juicebox.windowui.NormalizationType;
import org.broad.igv.feature.Chromosome;
import org.broad.igv.tdf.BufferedByteWriter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

public class CustomNormVectorFileHandler extends NormVectorUpdater {

    public static void updateHicFile(String path, String vectorPath) throws IOException {
        DatasetReaderV2 reader = new DatasetReaderV2(path);
        Dataset ds = reader.read();
        HiCGlobals.verifySupportedHiCFileVersion(reader.getVersion());

        String[] vectorPaths = vectorPath.split(",");
        Map<NormalizationType, Map<String, NormalizationVector>> allNormVectors = readVectorFile(vectorPaths, ds.getChromosomeHandler(), ds.getNormalizationHandler());
        NormVectorInfo normVectorInfo = completeCalculationsNecessaryForUpdatingCustomNormalizations(ds, allNormVectors, true);
        writeNormsToUpdateFile(reader, path, false, null, normVectorInfo.getExpectedValueFunctionMap(),
                normVectorInfo.getNormVectorIndices(), normVectorInfo.getNormVectorBuffer(), "Finished adding another normalization.");

        System.out.println("all custom norms added");
    }

    public static void unsafeHandleUpdatingOfNormalizations(SuperAdapter superAdapter, File[] files, boolean isControl) {

        Dataset ds = superAdapter.getHiC().getDataset();
        if (isControl) {
            ds = superAdapter.getHiC().getControlDataset();
        }

        String[] filePaths = new String[files.length];
        for (int i = 0; i < filePaths.length; i++) {
            filePaths[i] = files[i].getAbsolutePath();
        }

        try {
            Map<NormalizationType, Map<String, NormalizationVector>> allNormVectors = readVectorFile(filePaths,
                    ds.getChromosomeHandler(), ds.getNormalizationHandler());

            completeCalculationsNecessaryForUpdatingCustomNormalizations(ds, allNormVectors, false);

            for (NormalizationType customNormType : allNormVectors.keySet()) {
                ds.addNormalizationType(customNormType);
                for (NormalizationVector normalizationVector : allNormVectors.get(customNormType).values()) {
                    if (normalizationVector == null) {
                        System.out.println("error encountered");
                    }
                    ds.addNormalizationVectorDirectlyToRAM(normalizationVector);
                }
            }
            System.out.println("all custom norms added v2");

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private static NormVectorInfo completeCalculationsNecessaryForUpdatingCustomNormalizations(
            Dataset ds, Map<NormalizationType, Map<String, NormalizationVector>> normalizationVectorMap, boolean overwriteHicFileFooter) throws IOException {

        ChromosomeHandler chromosomeHandler = ds.getChromosomeHandler();
        Map<String, Integer> fragCountMap = ds.getFragmentCounts();
        List<HiCZoom> resolutions = ds.getAllPossibleResolutions();

        BufferedByteWriter normVectorBuffer = new BufferedByteWriter();
        List<NormalizationVectorIndexEntry> normVectorIndices = new ArrayList<>();
        Map<String, ExpectedValueFunction> expectedValueFunctionMap = ds.getExpectedValueFunctionMap();

        for (Iterator<Map.Entry<String, ExpectedValueFunction>> it = expectedValueFunctionMap.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, ExpectedValueFunction> entry = it.next();
            if (entry.getKey().contains("NONE")) {
                it.remove();
            }
        }

        // Loop through resolutions
        for (HiCZoom zoom : resolutions) {

            Map<String, Integer> fcm = zoom.getUnit() == HiC.Unit.FRAG ? fragCountMap : null;

            // Loop through chromosomes
            for (Chromosome chr : chromosomeHandler.getChromosomeArrayWithoutAllByAll()) {

                Matrix matrix = ds.getMatrix(chr, chr);

                if (matrix == null) continue;
                MatrixZoomData zd = matrix.getZoomData(zoom);

                if (true || overwriteHicFileFooter) {
                    // Get existing norm vectors so we don't lose them
                    for (NormalizationType type : NormalizationHandler.getAllNormTypes()) {
                        NormalizationVector existingNorm = ds.getNormalizationVector(chr.getIndex(), zoom, type);
                        if (existingNorm != null) {
                            int position = normVectorBuffer.bytesWritten();
                            putArrayValuesIntoBuffer(normVectorBuffer, existingNorm.getData());
                            int sizeInBytes = normVectorBuffer.bytesWritten() - position;
                            normVectorIndices.add(new NormalizationVectorIndexEntry(
                                    type.toString(), chr.getIndex(), zoom.getUnit().toString(), zoom.getBinSize(), position, sizeInBytes));
                        }
                    }
                }

                // add in all the new vectors
                for (NormalizationType customNormType : normalizationVectorMap.keySet()) {
                    ExpectedValueCalculation evLoaded = new ExpectedValueCalculation(chromosomeHandler, zoom.getBinSize(), fcm, customNormType);
                    if (true || overwriteHicFileFooter) {
                        handleLoadedVector(customNormType, chr.getIndex(), zoom, normalizationVectorMap.get(customNormType),
                                normVectorBuffer, normVectorIndices, zd, evLoaded);
                    }
                    String key = ExpectedValueFunctionImpl.getKey(zoom, customNormType);
                    expectedValueFunctionMap.put(key, evLoaded.getExpectedValueFunction());
                }
            }
        }

        ds.setExpectedValueFunctionMap(expectedValueFunctionMap);
        return new NormVectorInfo(normVectorBuffer, normVectorIndices, expectedValueFunctionMap);
    }

    private static void handleLoadedVector(NormalizationType customNormType, final int chrIndx, HiCZoom zoom, Map<String, NormalizationVector> normVectors,
                                           BufferedByteWriter normVectorBuffer, List<NormalizationVectorIndexEntry> normVectorIndex,
                                           MatrixZoomData zd, ExpectedValueCalculation evLoaded) throws IOException {

        String key = NormalizationVector.getKey(customNormType, chrIndx, zoom.getUnit().toString(), zoom.getBinSize());
        NormalizationVector vector = normVectors.get(key);
        if (vector == null) return;
        // Write loaded norm
        int position = normVectorBuffer.bytesWritten();
        putArrayValuesIntoBuffer(normVectorBuffer, vector.getData());

        int sizeInBytes = normVectorBuffer.bytesWritten() - position;
        normVectorIndex.add(new NormalizationVectorIndexEntry(
                customNormType.toString(), chrIndx, zoom.getUnit().toString(), zoom.getBinSize(), position, sizeInBytes));

        // Calculate the expected values
        Iterator<ContactRecord> iter = zd.contactRecordIterator();

        while (iter.hasNext()) {
            ContactRecord cr = iter.next();
            int x = cr.getBinX();
            int y = cr.getBinY();
            final float counts = cr.getCounts();
            if (isValidNormValue(vector.getData()[x]) & isValidNormValue(vector.getData()[y])) {
                double value = counts / (vector.getData()[x] * vector.getData()[y]);
                evLoaded.addDistance(chrIndx, x, y, value);
            }
        }
    }

    protected static Map<NormalizationType, Map<String, NormalizationVector>> readVectorFile(String[] fnames, ChromosomeHandler chromosomeHandler, NormalizationHandler normalizationHandler) throws IOException {

        Map<NormalizationType, Map<String, NormalizationVector>> normVectors = new HashMap<>();

        for (String fname : fnames) {
            BufferedReader vectorReader;
            if (fname.endsWith(".gz")) {
                InputStream fileStream = new FileInputStream(fname);
                InputStream gzipStream = new GZIPInputStream(fileStream);
                Reader decoder = new InputStreamReader(gzipStream, StandardCharsets.UTF_8);
                vectorReader = new BufferedReader(decoder, 4194304);
            } else {
                //this.reader = org.broad.igv.util.ParsingUtils.openBufferedReader(path);
                vectorReader = new BufferedReader(new InputStreamReader(new FileInputStream(fname)), HiCGlobals.bufferSize);
            }

            Chromosome chr = null;
            int resolution = -1;
            HiC.Unit unit = null;
            NormalizationType customNormType = null;

            String nextLine = vectorReader.readLine();
            while (nextLine != null) {
                // Header: vector  type  chr1    2048000 BP
                if (nextLine.startsWith("vector")) {
                    String[] tokens = nextLine.split("\\s+");
                    chr = chromosomeHandler.getChromosomeFromName(tokens[2]);
                    if (chr == null) {
                        System.err.println("Skipping " + tokens[2] + " which isn't in dataset");
                        nextLine = skipLinesUntilTextEncountered(vectorReader, "vector");
                        continue;
                    }

                    customNormType = normalizationHandler.getNormTypeFromString(tokens[1]);
                    resolution = Integer.valueOf(tokens[3]);
                    unit = HiC.Unit.valueOf(tokens[4]);
                }
                if (chr != null && customNormType != null) {
                    if (HiCGlobals.printVerboseComments) {
                        System.out.println("Adding norm " + customNormType + " for chr " + chr.getName() + " at " + resolution + " " + unit + " resolution.");
                    }

                    // Now do work on loaded norm vector
                    // Create the new vector by looping through the loaded vector file line by line
                    int size = chr.getLength() / resolution + 1;
                    double[] data = new double[size];
                    int i = 0;
                    nextLine = vectorReader.readLine();
                    // List<Double> data = new ArrayList<Double>();
                    while (nextLine != null && !(nextLine.startsWith("vector"))) {
                        if (nextLine.toLowerCase().equals("nan") || nextLine.equals(".")) {
                            data[i] = Double.NaN;
                        } else data[i] = Double.valueOf(nextLine);
                        i++;
                        if (i > size) {
                            throw new IOException("More values than resolution would indicate");
                        }
                        nextLine = vectorReader.readLine();
                    }

                    if (!normVectors.containsKey(customNormType)) {
                        normVectors.put(customNormType, new HashMap<String, NormalizationVector>());
                    }
                    NormalizationVector vector = new NormalizationVector(customNormType, chr.getIndex(), unit, resolution, data);
                    normVectors.get(customNormType).put(vector.getKey(), vector);

                } else {
                    System.err.println("Chromosome null"); // this shouldn't happen due to continue above
                }
            }
        }

        return normVectors;
    }

    private static String skipLinesUntilTextEncountered(BufferedReader vectorReader, String string) throws IOException {
        String nextLine = vectorReader.readLine();
        while (nextLine != null && !(nextLine.startsWith(string))) {
            nextLine = vectorReader.readLine();
        }
        return nextLine;
    }
}

/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2011-2016 Broad Institute, Aiden Lab
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

package juicebox.tools.utils.common;

import juicebox.HiC;
import juicebox.data.*;
import juicebox.windowui.HiCZoom;
import juicebox.windowui.NormalizationType;
import org.broad.igv.feature.Chromosome;

import java.io.IOException;
import java.util.Map;

/**
 * Utility functions to dump various bits of a hic file to stdout or file(s)
 *
 * @author jrobinso
 *         Date: 2/11/13
 *         Time: 2:01 PM
 */

class HiCFileUtils {

    private Dataset dataset;

    private HiCFileUtils(String hicfile) throws IOException {
        DatasetReaderV2 reader = new DatasetReaderV2(hicfile);
        dataset = reader.read();
    }

    public static void main(String[] args) throws IOException {
        HiCFileUtils utils = new HiCFileUtils(args[0]);
        utils.dumpNormalizationVectors("KR", "1", "BP", 250000);
        utils.dumpExpectedVectors("KR", "BP", 1000000);
    }

    private void dumpNormalizationVectors(String type, String chrName, String unitName, int binSize) {

        NormalizationType no = NormalizationType.valueOf(type);

        Chromosome chromosome = findChromosome(chrName);
        HiC.Unit unit = HiC.Unit.valueOf(unitName);
        HiCZoom zoom = new HiCZoom(unit, binSize);
        NormalizationVector nv = dataset.getNormalizationVector(chromosome.getIndex(), zoom, no);
        String label = "Normalization vector: type = " + type + " chr = " + chrName +
                " resolution = " + binSize + " " + unitName;
        System.out.println(label);
        double[] data = nv.getData();
        /*
        for(int i=0; i<data.length; i++) {
            System.out.println(data[i]);
        }
        */
        for (double datum : data) {
            System.out.println(datum);
        }

    }

    private void dumpExpectedVectors(String type, String unit, int binSize) {


        Map<String, ExpectedValueFunction> expValFunMap = dataset.getExpectedValueFunctionMap();
        for (Map.Entry<String, ExpectedValueFunction> entry : expValFunMap.entrySet()) {


            ExpectedValueFunctionImpl ev = (ExpectedValueFunctionImpl) entry.getValue();

            if (ev.getUnit().equals(unit) && ev.getBinSize() == binSize && ev.getNormalizationType().getLabel().equals(type)) {
                String label = ev.getNormalizationType() + "\t" + ev.getUnit() + "\t" + ev.getBinSize();

                System.out.println("Norm factors: " + label);
                for (Map.Entry<Integer, Double> nf : ev.getNormFactors().entrySet()) {
                    System.out.println(nf.getKey() + "\t" + nf.getValue());
                }

                System.out.println("Expected values: " + label);
                double[] values = ev.getExpectedValues();
                /*
                for (int i = 0; i < values.length; i++) {
                    System.out.println(values[i]);
                }
                */
                for (double datum : values) {
                    System.out.println(datum);
                }

                System.out.println("End expected values: " + label);
                System.out.println();
            }

        }

    }


    private Chromosome findChromosome(String name) {

        for (Chromosome chr : dataset.getChromosomes()) {
            if (chr.getName().equals(name)) return chr;
        }
        return null;
    }

}

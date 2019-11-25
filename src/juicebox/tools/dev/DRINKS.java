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

package juicebox.tools.dev;

import juicebox.HiCGlobals;
import juicebox.data.ChromosomeHandler;
import juicebox.data.Dataset;
import juicebox.data.HiCFileTools;
import juicebox.tools.clt.CommandLineParserForJuicer;
import juicebox.tools.clt.JuicerCLT;
import juicebox.windowui.NormalizationType;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DRINKS extends JuicerCLT {

    private final double maxPercentAllowedToBeZeroThreshold = 0.7;
    private final int maxIters = 10000;
    private final double logThreshold = 4;
    private final List<Dataset> datasetList = new ArrayList<>();
    private boolean doDifferentialClustering = false;
    private int resolution = 100000;
    private Dataset ds;
    private File outputDirectory;
    private int numClusters = 10;
    private List<String> inputHicFilePaths = new ArrayList<>();

    public DRINKS() {
        super("drinks [-r resolution] [-k NONE/VC/VC_SQRT/KR] [-m num_clusters] <input1.hic+input2.hic+input3.hic...> <output_file>");
        HiCGlobals.useCache = false;
    }

    @Override
    protected void readJuicerArguments(String[] args, CommandLineParserForJuicer juicerParser) {
        if (args.length != 3) {
            printUsageAndExit();
        }

        determineNumClusters(juicerParser);

        for (String path : args[1].split("\\+")) {
            System.out.println("Extracting " + path);
            inputHicFilePaths.add(path);
            List<String> tempList = new ArrayList<>();
            tempList.add(path);
            datasetList.add(HiCFileTools.extractDatasetForCLT(tempList, true));
        }
        ds = datasetList.get(0);
        outputDirectory = HiCFileTools.createValidDirectory(args[2]);

        NormalizationType preferredNorm = juicerParser.getNormalizationTypeOption(ds.getNormalizationHandler());
        if (preferredNorm != null) norm = preferredNorm;

        List<String> possibleResolutions = juicerParser.getMultipleResolutionOptions();
        if (possibleResolutions != null) {
            resolution = Integer.parseInt(possibleResolutions.get(0));
            if (possibleResolutions.size() > 1)
                System.err.println("Only one resolution can be specified for DRINKS\nUsing " + resolution);
        }
    }

    private void determineNumClusters(CommandLineParserForJuicer juicerParser) {
        int n = juicerParser.getMatrixSizeOption();
        if (n > 2) numClusters = n;
    }

    @Override
    public void run() {

        ChromosomeHandler chromosomeHandler = ds.getChromosomeHandler();

        DRINKSUtils.extractAllComparativeIntraSubcompartments(datasetList, chromosomeHandler, resolution, norm, logThreshold,
                maxPercentAllowedToBeZeroThreshold, numClusters, maxIters, outputDirectory, inputHicFilePaths);

        /*
        } else {
            GenomeWideList<SubcompartmentInterval> intraSubcompartments =
                    Clustering.extractAllInitialIntraSubcompartments(ds, chromosomeHandler, resolution, norm, logThreshold,
                            maxPercentAllowedToBeZeroThreshold, numClusters, maxIters);

            DrinkUtils.saveFileBeforeAndAfterCollapsing(intraSubcompartments, outputDirectory, "result_intra_initial.bed", "result_intra_initial_collapsed.bed");
            if (whichApproachtoUse == 1) {
                GenomeWideList<SubcompartmentInterval> finalSubcompartments = OriginalGWApproach.extractFinalGWSubcompartments(
                        ds, chromosomeHandler, resolution, norm, outputDirectory, numClusters, maxIters, logThreshold,
                        intraSubcompartments);
                DrinkUtils.saveFileBeforeAndAfterCollapsing(finalSubcompartments, outputDirectory, "gw_result_initial.bed", "gw_result_collapsed.bed");
            } else if (whichApproachtoUse == 2) {
                GenomeWideList<SubcompartmentInterval> finalSubcompartments = SecondGWApproach.extractFinalGWSubcompartments(
                        ds, chromosomeHandler, resolution, norm, outputDirectory, numClusters, maxIters, logThreshold,
                        intraSubcompartments, connectedComponentThreshold);
                finalSubcompartments.simpleExport(new File(outputDirectory, "final_stitched_collapsed_subcompartments.bed"));
            } else if (whichApproachtoUse == 3) {
                GenomeWideList<SubcompartmentInterval> finalSubcompartments = ThirdGWApproach.extractFinalGWSubcompartments(
                        ds, chromosomeHandler, resolution, norm, outputDirectory, numClusters, maxIters, logThreshold,
                        intraSubcompartments, connectedComponentThreshold);
                finalSubcompartments.simpleExport(new File(outputDirectory, "final_stitched_collapsed_subcompartments.bed"));
            }
        }

         */
    }

}
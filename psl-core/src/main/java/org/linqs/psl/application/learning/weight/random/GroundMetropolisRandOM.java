/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2015 The Regents of the University of California
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.linqs.psl.application.learning.weight.random;

import java.util.ArrayList;

import org.linqs.psl.config.ConfigBundle;
import org.linqs.psl.config.ConfigManager;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.model.weight.PositiveWeight;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link MetropolisRandOM} learning algorithm that samples a different weight
 * for each {@link WeightedGroundRule} but all those with the same parent
 * {@link WeightedRule} share a mean and a variance.
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class GroundMetropolisRandOM extends MetropolisRandOM {
	
	private static final Logger log = LoggerFactory.getLogger(GroundMetropolisRandOM.class);
	
	/**
	 * Prefix of property keys used by this class.
	 * 
	 * @see ConfigManager
	 */
	public static final String CONFIG_PREFIX = "groundrandom";
	
	/**
	 * Key for positive double to be used as variance in proposal distribution
	 */
	public static final String PROPOSAL_VARIANCE = CONFIG_PREFIX + ".proposalvariance";
	/** Default value for PROPOSAL_VARIANCE */
	public static final double PROPOSAL_VARIANCE_DEFAULT = .25;
	
	protected WeightedGroundRule[] gks;
	protected int[] cumulativeGroundings;
	protected double[] currentWeights, previousWeights, sum, sumSq;
	
	protected double proposalVariance;

	public GroundMetropolisRandOM(Model model, Database rvDB, Database observedDB, ConfigBundle config) {
		super(model, rvDB, observedDB, config);
		proposalVariance = config.getDouble(PROPOSAL_VARIANCE, PROPOSAL_VARIANCE_DEFAULT);
		if (proposalVariance <= 0.0)
			throw new IllegalArgumentException("Proposal variance must be positive.");
	}
	
	@Override
	protected void doLearn() {
		
		/* Collects the GroundCompatibilityKernels */
		cumulativeGroundings = new int[kernels.size()];
		ArrayList<WeightedGroundRule> tempGroundKernels = new ArrayList<WeightedGroundRule>(reasoner.size());
		for (int i = 0; i < kernels.size(); i++) {
			for (GroundRule gk : reasoner.getGroundKernels(kernels.get(i)))
				tempGroundKernels.add((WeightedGroundRule) gk);
			cumulativeGroundings[i] = tempGroundKernels.size();
		}
		gks = tempGroundKernels.toArray(new WeightedGroundRule[tempGroundKernels.size()]);
		log.info("Learning with {} ground kernels.", gks.length);
		
		/* Initializes weights */
		currentWeights = new double[gks.length];
		previousWeights = new double[gks.length];
		for (int i = 0; i < previousWeights.length; i++)
			previousWeights[i] = gks[i].getWeight().getWeight();
		
		/* Initializes arrays for statistics collection */
		sum = new double[kernels.size()];
		sumSq = new double[kernels.size()];
		
		/* Begins learning */
		super.doLearn();
	}

	@Override
	protected void prepareForRound() {
		for (int i = 0; i < kernels.size(); i++) {
			sum[i] = 0.0;
			sumSq[i] = 0.0;
		}
		
		/* Resets the weights to the new means */
		int currentKernelIndex = 0;
		for (int i = 0; i < gks.length; i++) {
			while (i >= cumulativeGroundings[currentKernelIndex])
				currentKernelIndex++;
			gks[i].setWeight(new PositiveWeight(Math.max(0.0, kernelMeans[currentKernelIndex])));
		}
		reasoner.changedGroundKernelWeights();
	}

	@Override
	protected void sampleAndSetWeights() {
		int currentKernelIndex = 0;
		for (int i = 0; i < gks.length; i++) {
			while (i >= cumulativeGroundings[currentKernelIndex])
				currentKernelIndex++;
			currentWeights[i] = sampleFromGaussian(previousWeights[i], proposalVariance);
//			currentWeights[i] = sampleFromGaussian(previousWeights[i], Math.min(proposalVariance, kernelVariances[currentKernelIndex]));
			gks[i].setWeight(new PositiveWeight(Math.max(0.0, currentWeights[i])));
		}
	}

	@Override
	protected double getLogLikelihoodSampledWeights() {
		double likelihood = 0.0;
		int currentKernelIndex = 0;
		for (int i = 0; i < gks.length; i++) {
			while (i >= cumulativeGroundings[currentKernelIndex])
				currentKernelIndex++;
//			likelihood -= Math.pow(currentWeights[i] - kernelMeans[currentKernelIndex], 2) / (2 * kernelVariances[currentKernelIndex]);
			likelihood -= Math.pow(currentWeights[i] - kernelMeans[currentKernelIndex], 2) / (2 * initialVariance);
//			likelihood -= Math.abs(currentWeights[i] - kernelMeans[currentKernelIndex]) / (2 * initialVariance);
			
		}
		return likelihood;
	}

	@Override
	protected void acceptSample(boolean burnIn) {
		int currentKernelIndex = 0;
		for (int i = 0; i < gks.length; i++) {
			previousWeights[i] = currentWeights[i];
			if (!burnIn) {
				while (i >= cumulativeGroundings[currentKernelIndex])
					currentKernelIndex++;
				sum[currentKernelIndex] += currentWeights[i];
				sumSq[currentKernelIndex] += currentWeights[i] * currentWeights[i];
			}
		}
	}

	@Override
	protected void rejectSample(boolean burnIn) {
		int currentKernelIndex = 0;
		for (int i = 0; i < gks.length; i++) {
			if (!burnIn) {
				while (i >= cumulativeGroundings[currentKernelIndex])
					currentKernelIndex++;
				sum[currentKernelIndex] += previousWeights[i];
				sumSq[currentKernelIndex] += previousWeights[i] * previousWeights[i];
			}
		}
	}

	@Override
	protected void finishRound() {
		for (int i = 0; i < kernels.size(); i++) {
			int numGroundings = (i == 0) ? cumulativeGroundings[0] : cumulativeGroundings[i] - cumulativeGroundings[i-1];
			kernelMeans[i] = sum[i] / ((numSamples - burnIn) * numGroundings);
			kernelVariances[i] = sumSq[i] / ((numSamples - burnIn) * numGroundings) - kernelMeans[i] * kernelMeans[i];
			kernelVariances[i] = Math.max(kernelVariances[i], 1e-3);
			log.info("Variance of {} for kernel {}", kernelVariances[i], kernels.get(i)); 
		}
	}

	@Override
	protected void updateProposalVariance(int accepted, int count) {
		if (count > 0 && count % 5 == 0) {
			proposalVariance *= ((double) accepted / count) / 0.5;
			log.info("Acceptance rate is {}. Updated variance to {}", (double) accepted / count, proposalVariance);
		}
	}

}
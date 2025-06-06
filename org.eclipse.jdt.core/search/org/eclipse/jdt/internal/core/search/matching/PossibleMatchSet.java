/*******************************************************************************
 * Copyright (c) 2000, 2008 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.core.search.matching;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.internal.compiler.util.ObjectVector;

/**
 * A set of PossibleMatches that is sorted by package fragment roots.
 */
public class PossibleMatchSet {

private Map<IPath, ObjectVector> rootsToPossibleMatches = new HashMap<>();
private int elementCount = 0;

public void add(PossibleMatch possibleMatch) {
	IPath path = possibleMatch.openable.getPackageFragmentRoot().getPath();
	ObjectVector possibleMatches = this.rootsToPossibleMatches.get(path);
	if (possibleMatches != null) {
		PossibleMatch storedMatch = (PossibleMatch) possibleMatches.find(possibleMatch);
		if (storedMatch != null) {
			while (storedMatch.getSimilarMatch() != null) {
				storedMatch = storedMatch.getSimilarMatch();
			}
			storedMatch.setSimilarMatch(possibleMatch);
			return;
		}
	} else{
		this.rootsToPossibleMatches.put(path, possibleMatches = new ObjectVector());
	}

	possibleMatches.add(possibleMatch);
	this.elementCount++;
}
public PossibleMatch[] getPossibleMatches(IPackageFragmentRoot[] roots) {
	PossibleMatch[] result = new PossibleMatch[this.elementCount];
	int index = 0;
	HashSet<IPath> processedHash = new HashSet<>();
	for (IPackageFragmentRoot root : roots) {
		IPath path = root.getPath();
		ObjectVector possibleMatches = this.rootsToPossibleMatches.get(path);
		if (possibleMatches != null && !processedHash.contains(path)) {
			possibleMatches.copyInto(result, index);
			index += possibleMatches.size();
			processedHash.add(path);
		}
	}
	if (index < this.elementCount)
		System.arraycopy(result, 0, result = new PossibleMatch[index], 0, index);
	return result;
}
public void reset() {
	this.rootsToPossibleMatches = new HashMap<>();
	this.elementCount = 0;
}
}

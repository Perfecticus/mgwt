package com.googlecode.mgwt.linker.linker;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;

import com.google.gwt.core.ext.LinkerContext;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.TreeLogger.Type;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.core.ext.linker.AbstractLinker;
import com.google.gwt.core.ext.linker.ArtifactSet;
import com.google.gwt.core.ext.linker.ConfigurationProperty;
import com.google.gwt.core.ext.linker.EmittedArtifact;
import com.google.gwt.core.ext.linker.LinkerOrder;
import com.google.gwt.core.ext.linker.Shardable;
import com.google.gwt.core.ext.linker.SyntheticArtifact;
import com.google.gwt.core.ext.linker.impl.SelectionInformation;
import com.googlecode.mgwt.linker.server.BindingProperty;

@LinkerOrder(LinkerOrder.Order.POST)
@Shardable
public class PermutationMapLinker extends AbstractLinker {

	public static final String EXTERNAL_FILES_CONFIGURATION_PROPERTY_NAME = "html5manifestlinker_files";
	public static final String PERMUTATION_MANIFEST_FILE_ENDING = ".manifest";
	public static final String PERMUTATION_FILE_ENDING = ".perm.xml";
	public static final String MANIFEST_MAP_FILE_NAME = "manifest.map";

	private final XMLPermutationProvider xmlPermutationProvider;

	public PermutationMapLinker() {
		xmlPermutationProvider = new XMLPermutationProvider();
		manifestWriter = new ManifestWriter();
	}

	private final ManifestWriter manifestWriter;

	@Override
	public String getDescription() {
		return "PermutationMapLinker";
	}

	@Override
	public ArtifactSet link(final TreeLogger logger, final LinkerContext context, final ArtifactSet artifacts,
			final boolean onePermutation) throws UnableToCompleteException {
		if (onePermutation) {
			final Map<String, Set<BindingProperty>> permutationMap = buildPermutationMap(logger, context, artifacts);
			final Set<Entry<String, Set<BindingProperty>>> entrySet = permutationMap.entrySet();

			// since we are in onePermutation there should be just one
			// strongName
			// better make sure..
			if (permutationMap.size() != 1) {
				logger.log(Type.ERROR, "There should be only one permutation right now, but there were: '"
						+ permutationMap.size() + "'");
				throw new UnableToCompleteException();
			}

			final Entry<String, Set<BindingProperty>> next = entrySet.iterator().next();
			final String strongName = next.getKey();
			final Set<BindingProperty> bindingProperties = next.getValue();

			// all artifacts for this compilation
			final Set<String> artifactsForCompilation = getArtifactsForCompilation(logger, context, artifacts);

			final ArtifactSet toReturn = new ArtifactSet(artifacts);
			final PermutationArtifact permutationArtifact = new PermutationArtifact(PermutationMapLinker.class, strongName, artifactsForCompilation, bindingProperties);
			toReturn.add(permutationArtifact);
			return toReturn;
		}

		final ArtifactSet toReturn = new ArtifactSet(artifacts);
		final Map<String, Set<BindingProperty>> map = buildPermutationMap(logger, context, artifacts);

		if (map.size() == 0) {
			// hosted mode
			return toReturn;
		}

		final Map<String, PermutationArtifact> permutationArtifactAsMap = getPermutationArtifactAsMap(artifacts);

		final Set<String> externalFiles = getExternalFiles(logger, context);

		final Set<String> allPermutationFiles = getAllPermutationFiles(permutationArtifactAsMap);

		// get all artifacts
		final Set<String> allArtifacts = getArtifactsForCompilation(logger, context, artifacts);

		for (final Entry<String, PermutationArtifact> entry : permutationArtifactAsMap.entrySet()) {
			final PermutationArtifact permutationArtifact = entry.getValue();
			// make a copy of all artifacts
			final HashSet<String> filesForCurrentPermutation = new HashSet<String>(allArtifacts);
			// remove all permutations
			filesForCurrentPermutation.removeAll(allPermutationFiles);
			// add files of the one permutation we are interested in
			// leaving the common stuff for all permutations in...
			filesForCurrentPermutation.addAll(entry.getValue().getPermutationFiles());

			final String permXml = buildPermXml(logger, permutationArtifact, filesForCurrentPermutation, externalFiles);

			// emit permutation information file
			final SyntheticArtifact emitString = emitString(logger, permXml, permutationArtifact.getPermutationName()
					+ PERMUTATION_FILE_ENDING);
			toReturn.add(emitString);

			// build manifest
			final String maniFest = buildManiFest(entry.getKey(), filesForCurrentPermutation, externalFiles);
			toReturn.add(emitString(logger, maniFest, entry.getKey() + PERMUTATION_MANIFEST_FILE_ENDING));

		}

		toReturn.add(createPermutationMap(logger, map));
		return toReturn;

	}

	protected String buildPermXml(final TreeLogger logger, final PermutationArtifact permutationArtifact,
			final Set<String> gwtCompiledFiles, final Set<String> otherResources) throws UnableToCompleteException {
		final HashSet<String> namesForPermXml = new HashSet<String>(gwtCompiledFiles);
		namesForPermXml.addAll(otherResources);

		try {
			return xmlPermutationProvider.writePermutationInformation(permutationArtifact.getPermutationName(), permutationArtifact.getBindingProperties(), namesForPermXml);
		} catch (final XMLPermutationProviderException e) {
			logger.log(Type.ERROR, "can not build xml for permutation file", e);
			throw new UnableToCompleteException();
		}

	}

	/**
	 * @param permutationArtifactAsMap
	 * @return
	 */
	protected Set<String> getAllPermutationFiles(final Map<String, PermutationArtifact> permutationArtifactAsMap) {
		final Set<String> allPermutationFiles = new HashSet<String>();

		for (final Entry<String, PermutationArtifact> entry : permutationArtifactAsMap.entrySet()) {
			allPermutationFiles.addAll(entry.getValue().getPermutationFiles());
		}
		return allPermutationFiles;
	}

	protected Map<String, PermutationArtifact> getPermutationArtifactAsMap(final ArtifactSet artifacts) {
		final Map<String, PermutationArtifact> hashMap = new HashMap<String, PermutationArtifact>();
		for (final PermutationArtifact permutationArtifact : artifacts.find(PermutationArtifact.class)) {
			hashMap.put(permutationArtifact.getPermutationName(), permutationArtifact);
		}
		return hashMap;
	}

	protected boolean shouldArtifactBeInManifest(final String pathName) {
		if (pathName.endsWith("symbolMap") || pathName.endsWith(".xml.gz") || pathName.endsWith("rpc.log")
				|| pathName.endsWith("gwt.rpc") || pathName.endsWith("manifest.txt")
				|| pathName.startsWith("rpcPolicyManifest") || pathName.startsWith("soycReport")
				|| pathName.endsWith(".cssmap")) { return false; }

		// TODO reg exp

		return true;
	}

	protected Set<String> getArtifactsForCompilation(final TreeLogger logger, final LinkerContext context,
			final ArtifactSet artifacts) {
		final Set<String> artifactNames = new HashSet<String>();
		for (final EmittedArtifact artifact : artifacts.find(EmittedArtifact.class)) {
			final String pathName = artifact.getPartialPath();

			if (shouldArtifactBeInManifest(pathName)) {
				artifactNames.add(context.getModuleName() + "/" + pathName);
			}
		}

		return artifactNames;

	}

	protected String buildManiFest(final String moduleName, final Set<String> cacheResources,
			final Set<String> externalFiles) {
		return manifestWriter.writeManifest(externalFiles, cacheResources);
	}

	protected Set<String> getExternalFiles(final TreeLogger logger, final LinkerContext context) {
		final HashSet<String> set = new HashSet<String>();
		final SortedSet<ConfigurationProperty> properties = context.getConfigurationProperties();
		for (final ConfigurationProperty configurationProperty : properties) {
			final String name = configurationProperty.getName();
			if (EXTERNAL_FILES_CONFIGURATION_PROPERTY_NAME.equals(name)) {
				for (final String value : configurationProperty.getValues()) {
					set.add(value);
				}
			}
		}

		return set;
	}

	protected EmittedArtifact createPermutationMap(final TreeLogger logger, final Map<String, Set<BindingProperty>> map)
			throws UnableToCompleteException {

		try {
			final String string = xmlPermutationProvider.serializeMap(map);
			return emitString(logger, string, MANIFEST_MAP_FILE_NAME);
		} catch (final XMLPermutationProviderException e) {
			logger.log(Type.ERROR, "can not build manifest map", e);
			throw new UnableToCompleteException();
		}

	}

	protected Map<String, Set<BindingProperty>> buildPermutationMap(final TreeLogger logger,
			final LinkerContext context, final ArtifactSet artifacts) throws UnableToCompleteException {

		final HashMap<String, Set<BindingProperty>> map = new HashMap<String, Set<BindingProperty>>();

		for (final SelectionInformation result : artifacts.find(SelectionInformation.class)) {
			final Set<BindingProperty> list = new HashSet<BindingProperty>();
			map.put(result.getStrongName(), list);

			final TreeMap<String, String> propMap = result.getPropMap();
			final Set<Entry<String, String>> set = propMap.entrySet();

			for (final Entry<String, String> entry : set) {
				final BindingProperty bindingProperty = new BindingProperty(entry.getKey(), entry.getValue());
				list.add(bindingProperty);
			}

		}
		return map;
	}
}

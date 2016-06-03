package org.jboss.windup.rules.apps.java.scan.provider;

import org.jboss.windup.config.AbstractRuleProvider;
import org.jboss.windup.config.GraphRewrite;
import org.jboss.windup.config.metadata.RuleMetadata;
import org.jboss.windup.config.operation.Commit;
import org.jboss.windup.config.operation.IterationProgress;
import org.jboss.windup.config.operation.iteration.AbstractIterationOperation;
import org.jboss.windup.config.phase.ArchiveExtractionPhase;
import org.jboss.windup.config.query.Query;
import org.jboss.windup.graph.GraphContext;
import org.jboss.windup.graph.model.ArchiveModel;
import org.jboss.windup.graph.model.DuplicateArchiveModel;
import org.jboss.windup.graph.model.resource.FileModel;
import org.jboss.windup.graph.service.GraphService;
import org.jboss.windup.rules.apps.java.archives.model.IgnoredArchiveModel;
import org.jboss.windup.rules.apps.java.scan.operation.UnzipArchiveToOutputFolder;
import org.ocpsoft.rewrite.config.Configuration;
import org.ocpsoft.rewrite.config.ConfigurationBuilder;
import org.ocpsoft.rewrite.context.EvaluationContext;

/**
 * Unzip archives from the input application.
 *
 * @author <a href="mailto:jesse.sightler@gmail.com">Jesse Sightler</a>
 */
@RuleMetadata(phase = ArchiveExtractionPhase.class)
public class UnzipArchivesToOutputRuleProvider extends AbstractRuleProvider
{
    @Override
    public Configuration getConfiguration(GraphContext context)
    {
        return ConfigurationBuilder.begin()
            .addRule()
            .when(Query.fromType(ArchiveModel.class).excludingType(IgnoredArchiveModel.class))
            .perform(
                UnzipArchiveToOutputFolder.unzip(),
                IterationProgress.monitoring("Unzipped archive", 1),
                Commit.every(1)
            )
            .addRule()
            .when(Query.fromType(ArchiveModel.class).excludingType(DuplicateArchiveModel.class))
            .perform(new DuplicateArchiveOperation());
    }

    /**
     * Processes {@link ArchiveModel}s and makes sure that any that have duplicates are removed from the tree and
     * replaced with a {@link DuplicateArchiveModel} that links to them.
     */
    private class DuplicateArchiveOperation extends AbstractIterationOperation<ArchiveModel>
    {

        @Override
        public void perform(GraphRewrite event, EvaluationContext context, ArchiveModel canonicalArchive)
        {
            // Skip if there were no duplicates
            if (!canonicalArchive.getDuplicateArchives().iterator().hasNext())
            {
                return;
            }

            // Get the canonical archive and remove it from its current position in the tree
            ArchiveModel canonicalParentArchive = canonicalArchive.getParentArchive();
            FileModel canonicalArchiveParentFile = canonicalArchive.getParentFile();
            canonicalArchive.setParentFile(null);
            canonicalArchive.setParentArchive(null);

            /*
             * Create the duplicate archive, link it to the canonical archive and
             * place it in the tree.
             *
             * Essentially this will replace the canonical Archive vertex with a vertex that points back to the
             * single canonical source for this archive.
             */
            GraphService<DuplicateArchiveModel> duplicateArchiveService = event.getGraphContext().service(DuplicateArchiveModel.class);
            DuplicateArchiveModel duplicateArchive = duplicateArchiveService.create();
            duplicateArchive.setCanonicalArchive(canonicalArchive);
            duplicateArchive.setSHA1Hash(canonicalArchive.getSHA1Hash());
            duplicateArchive.setFilePath(canonicalArchive.getFilePath());
            duplicateArchive.setArchiveName(canonicalArchive.getArchiveName());
            duplicateArchive.setFileName(canonicalArchive.getFileName());
            duplicateArchive.setParentArchive(canonicalParentArchive);
            duplicateArchive.setParentFile(canonicalArchiveParentFile);
        }
    }
}
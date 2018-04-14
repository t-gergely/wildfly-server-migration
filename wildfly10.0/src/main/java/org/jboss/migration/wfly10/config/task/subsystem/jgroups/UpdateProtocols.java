/*
 * Copyright 2016 Red Hat, Inc.
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
package org.jboss.migration.wfly10.config.task.subsystem.jgroups;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.migration.core.env.TaskEnvironment;
import org.jboss.migration.core.task.ServerMigrationTaskResult;
import org.jboss.migration.core.task.TaskContext;
import org.jboss.migration.wfly10.config.management.ManageableServerConfiguration;
import org.jboss.migration.wfly10.config.management.SubsystemResource;
import org.jboss.migration.wfly10.config.task.management.subsystem.UpdateSubsystemResourceSubtaskBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

/**
 * A task which updates jgroup subsystem configuration's protocols.
 * @author emmartins
 */
public class UpdateProtocols<S> extends UpdateSubsystemResourceSubtaskBuilder<S> {

    private final List<Operation> operations;

    public UpdateProtocols(Operations operations) {
        subtaskName("update-protocols");
        this.operations = Collections.unmodifiableList(operations.operations);
    }

    private static final String STACK = "stack";
    private static final String PROTOCOL = "protocol";

    @Override
    protected ServerMigrationTaskResult updateConfiguration(ModelNode config, S source, SubsystemResource subsystemResource, TaskContext context, TaskEnvironment taskEnvironment) {
        final PathAddress subsystemPathAddress = subsystemResource.getResourcePathAddress();
        final ManageableServerConfiguration serverConfiguration = subsystemResource.getServerConfiguration();

        final ModelNode stacks = config.get(STACK);
        if (!stacks.isDefined()) {
            context.getLogger().debugf("No stacks defined.");
            return ServerMigrationTaskResult.SKIPPED;
        }
        final Set<String> protocolsRemoved = new HashSet<>();
        final Set<String> protocolsAdded = new HashSet<>();

        final org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder compositeOperationBuilder = org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder.create();
        for (String stackName : stacks.keys()) {
            final ProtocolStack protocolStack = new ProtocolStack(stackName, config.get(STACK, stackName));
            for (Operation operation : operations) {
                operation.execute(protocolStack);
            }
            final Set<String> protocolsRemovedFromStack = protocolStack.getProtocolsRemoved();
            final Set<String> protocolsAddedToStack = protocolStack.getProtocolsAdded();
            if (!protocolsRemovedFromStack.isEmpty() || !protocolsAddedToStack.isEmpty()) {
                // protocols order matters so...
                // first remove the old set
                for (Property protocol : protocolStack.sourceProtocols) {
                    compositeOperationBuilder.addStep(Util.createRemoveOperation(subsystemPathAddress.append(STACK, stackName).append(PROTOCOL, protocol.getName())));
                }
                // then add the new set
                for (Property protocol : protocolStack.targetProtocols) {
                    final ModelNode addOp = protocol.getValue().clone();
                    addOp.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.ADD);
                    addOp.get(ModelDescriptionConstants.ADDRESS).set(subsystemPathAddress.append(STACK, stackName).append(PROTOCOL, protocol.getName()).toModelNode());
                    compositeOperationBuilder.addStep(addOp);
                }
                protocolsRemoved.addAll(protocolsRemovedFromStack);
                protocolsAdded.addAll(protocolsAddedToStack);
            }
        }

        if (protocolsRemoved.isEmpty() && protocolsAdded.isEmpty()) {
            context.getLogger().debugf("No protocols removed or added.");
            return ServerMigrationTaskResult.SKIPPED;
        }
        serverConfiguration.executeManagementOperation(compositeOperationBuilder.build().getOperation());
        return new ServerMigrationTaskResult.Builder()
                .success()
                .addAttribute("protocols-removed", protocolsRemoved)
                .addAttribute("protocols-added", protocolsAdded)
                .build();
    }

    static class ProtocolStack {

        final List<Property> sourceProtocols;
        final List<Property> targetProtocols;
        final String name;

        ProtocolStack(String name, ModelNode stackModelNode) {
            this.name = name;
            final ModelNode protocolsModelNode = stackModelNode.get(PROTOCOL);
            this.sourceProtocols = protocolsModelNode.clone().asPropertyList();
            this.targetProtocols = protocolsModelNode.clone().asPropertyList();
        }

        void add(String protocol) {
            targetProtocols.add(new Property(protocol, new ModelNode()));
        }

        void replace(String oldProtocol, String newProtocol) {
            final ListIterator<Property> li = targetProtocols.listIterator();
            while (li.hasNext()) {
                if (li.next().getName().equals(oldProtocol)) {
                    li.set(new Property(newProtocol, new ModelNode()));
                }
            }
        }

        boolean remove(String protocol) {
            final ListIterator<Property> li = targetProtocols.listIterator();
            while (li.hasNext()) {
                if (li.next().getName().equals(protocol)) {
                    li.remove();
                    return true;
                }
            }
            return false;
        }

        Set<String> getProtocolsAdded() {
            final Set<String> result = new HashSet<>();
            for (Property protocol : targetProtocols) {
                result.add(protocol.getName());
            }
            for (Property protocol : sourceProtocols) {
                result.remove(protocol.getName());
            }
            return result;
        }

        Set<String> getProtocolsRemoved() {
            final Set<String> result = new HashSet<>();
            for (Property protocol : sourceProtocols) {
                result.add(protocol.getName());
            }
            for (Property protocol : targetProtocols) {
                result.remove(protocol.getName());
            }
            return result;
        }
    }

    interface Operation {
        void execute(ProtocolStack protocolStack);
    }

    public static class Operations {

        final List<Operation> operations = new ArrayList<>();

        public Operations add(String protocol) {
            operations.add(protocolStack -> protocolStack.add(protocol));
            return this;
        }

        public Operations replace(String oldProtocol, String newProtocol) {
            operations.add(protocolStack -> protocolStack.replace(oldProtocol, newProtocol));
            return this;
        }

        public Operations remove(String protocol) {
            operations.add(protocolStack -> protocolStack.remove(protocol));
            return this;
        }
    }
}
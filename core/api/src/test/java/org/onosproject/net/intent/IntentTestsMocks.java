/*
 * Copyright 2014 Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onosproject.net.intent;

import static org.onosproject.net.NetTestTools.createPath;
import static org.onosproject.net.NetTestTools.did;
import static org.onosproject.net.NetTestTools.link;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.onosproject.core.DefaultGroupId;
import org.onosproject.core.GroupId;
import org.onosproject.net.DeviceId;
import org.onosproject.net.ElementId;
import org.onosproject.net.Link;
import org.onosproject.net.NetTestTools;
import org.onosproject.net.NetworkResource;
import org.onosproject.net.Path;
import org.onosproject.net.flow.FlowId;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.criteria.Criterion.Type;
import org.onosproject.net.flow.instructions.Instruction;
import org.onosproject.net.resource.BandwidthResourceRequest;
import org.onosproject.net.resource.LambdaResourceRequest;
import org.onosproject.net.resource.LinkResourceAllocations;
import org.onosproject.net.resource.LinkResourceListener;
import org.onosproject.net.resource.LinkResourceRequest;
import org.onosproject.net.resource.LinkResourceService;
import org.onosproject.net.resource.ResourceAllocation;
import org.onosproject.net.resource.ResourceRequest;
import org.onosproject.net.resource.ResourceType;
import org.onosproject.net.topology.DefaultTopologyEdge;
import org.onosproject.net.topology.DefaultTopologyVertex;
import org.onosproject.net.topology.LinkWeight;
import org.onosproject.net.topology.PathService;
import org.onosproject.net.topology.TopologyVertex;
import org.onosproject.store.Timestamp;

import com.google.common.base.MoreObjects;

/**
 * Common mocks used by the intent framework tests.
 */
public class IntentTestsMocks {
    /**
     * Mock traffic selector class used for satisfying API requirements.
     */
    public static class MockSelector implements TrafficSelector {
        @Override
        public Set<Criterion> criteria() {
            return new HashSet<>();
        }

        @Override
        public Criterion getCriterion(Type type) {
            return null;
        }
    }

    /**
     * Mock traffic treatment class used for satisfying API requirements.
     */
    public static class MockTreatment implements TrafficTreatment {
        @Override
        public List<Instruction> instructions() {
            return new ArrayList<>();
        }
    }

    /**
     * Mock path service for creating paths within the test.
     */
    public static class MockPathService implements PathService {

        final String[] pathHops;
        final String[] reversePathHops;

        /**
         * Constructor that provides a set of hops to mock.
         *
         * @param pathHops path hops to mock
         */
        public MockPathService(String[] pathHops) {
            this.pathHops = pathHops;
            String[] reversed = pathHops.clone();
            Collections.reverse(Arrays.asList(reversed));
            reversePathHops = reversed;
        }

        @Override
        public Set<Path> getPaths(ElementId src, ElementId dst) {
            Set<Path> result = new HashSet<>();

            String[] allHops = new String[pathHops.length];

            if (src.toString().endsWith(pathHops[0])) {
                System.arraycopy(pathHops, 0, allHops, 0, pathHops.length);
            } else {
                System.arraycopy(reversePathHops, 0, allHops, 0, pathHops.length);
            }

            result.add(createPath(allHops));
            return result;
        }

        @Override
        public Set<Path> getPaths(ElementId src, ElementId dst, LinkWeight weight) {
            final Set<Path> paths = getPaths(src, dst);

            for (Path path : paths) {
                final DeviceId srcDevice = path.src().deviceId();
                final DeviceId dstDevice = path.dst().deviceId();
                final TopologyVertex srcVertex = new DefaultTopologyVertex(srcDevice);
                final TopologyVertex dstVertex = new DefaultTopologyVertex(dstDevice);
                final Link link = link(src.toString(), 1, dst.toString(), 1);

                final double weightValue = weight.weight(new DefaultTopologyEdge(srcVertex, dstVertex, link));
                if (weightValue < 0) {
                    return new HashSet<>();
                }
            }
            return paths;
        }
    }

    public static class MockLinkResourceAllocations implements LinkResourceAllocations {
        @Override
        public Set<ResourceAllocation> getResourceAllocation(Link link) {
            return null;
        }

        @Override
        public IntentId intendId() {
            return null;
        }

        @Override
        public Collection<Link> links() {
            return null;
        }

        @Override
        public Set<ResourceRequest> resources() {
            return null;
        }

        @Override
        public ResourceType type() {
            return null;
        }
    }

    public static class MockedAllocationFailure extends RuntimeException { }

    public static class MockResourceService implements LinkResourceService {

        double availableBandwidth = -1.0;
        int availableLambda = -1;

        /**
         * Allocates a resource service that will allow bandwidth allocations
         * up to a limit.
         *
         * @param bandwidth available bandwidth limit
         * @return resource manager for bandwidth requests
         */
        public static MockResourceService makeBandwidthResourceService(double bandwidth) {
            final MockResourceService result = new MockResourceService();
            result.availableBandwidth = bandwidth;
            return result;
        }

        /**
         * Allocates a resource service that will allow lambda allocations.
         *
         * @param lambda Lambda to return for allocation requests. Currently unused
         * @return resource manager for lambda requests
         */
        public static MockResourceService makeLambdaResourceService(int lambda) {
            final MockResourceService result = new MockResourceService();
            result.availableLambda = lambda;
            return result;
        }

        public void setAvailableBandwidth(double availableBandwidth) {
            this.availableBandwidth = availableBandwidth;
        }

        public void setAvailableLambda(int availableLambda) {
            this.availableLambda = availableLambda;
        }


        @Override
        public LinkResourceAllocations requestResources(LinkResourceRequest req) {
            int lambda = -1;
            double bandwidth = -1.0;

            for (ResourceRequest resourceRequest : req.resources()) {
                if (resourceRequest.type() == ResourceType.BANDWIDTH) {
                    final BandwidthResourceRequest brr = (BandwidthResourceRequest) resourceRequest;
                    bandwidth = brr.bandwidth().toDouble();
                } else if (resourceRequest.type() == ResourceType.LAMBDA) {
                    lambda = 1;
                }
            }

            if (availableBandwidth < bandwidth) {
                throw new MockedAllocationFailure();
            }
            if (lambda > 0 && availableLambda == 0) {
                throw new MockedAllocationFailure();
            }

            return new IntentTestsMocks.MockLinkResourceAllocations();
        }

        @Override
        public void releaseResources(LinkResourceAllocations allocations) {
            // Mock
        }

        @Override
        public LinkResourceAllocations updateResources(LinkResourceRequest req,
                                                       LinkResourceAllocations oldAllocations) {
            return null;
        }

        @Override
        public Iterable<LinkResourceAllocations> getAllocations() {
            return null;
        }

        @Override
        public Iterable<LinkResourceAllocations> getAllocations(Link link) {
            return null;
        }

        @Override
        public LinkResourceAllocations getAllocations(IntentId intentId) {
            return null;
        }

        @Override
        public Iterable<ResourceRequest> getAvailableResources(Link link) {
            final List<ResourceRequest> result = new LinkedList<>();
            if (availableBandwidth > 0.0) {
                result.add(new BandwidthResourceRequest(availableBandwidth));
            }
            if (availableLambda > 0) {
                result.add(new LambdaResourceRequest());
            }
            return result;
        }

        @Override
        public Iterable<ResourceRequest> getAvailableResources(Link link, LinkResourceAllocations allocations) {
            return null;
        }

        @Override
        public void addListener(LinkResourceListener listener) {

        }

        @Override
        public void removeListener(LinkResourceListener listener) {

        }
    }

    private static final IntentTestsMocks.MockSelector SELECTOR =
            new IntentTestsMocks.MockSelector();
    private static final IntentTestsMocks.MockTreatment TREATMENT =
            new IntentTestsMocks.MockTreatment();

    public static class MockFlowRule implements FlowRule {

        int priority;
        Type type;

        public MockFlowRule(int priority) {
            this.priority = priority;
            this.type = Type.DEFAULT;
        }

        @Override
        public FlowId id() {
            return FlowId.valueOf(1);
        }

        @Override
        public short appId() {
            return 0;
        }

        @Override
        public GroupId groupId() {
            return new DefaultGroupId(0);
        }

        @Override
        public int priority() {
            return priority;
        }

        @Override
        public DeviceId deviceId() {
            return did("1");
        }

        @Override
        public TrafficSelector selector() {
            return SELECTOR;
        }

        @Override
        public TrafficTreatment treatment() {
            return TREATMENT;
        }

        @Override
        public int timeout() {
            return 0;
        }

        @Override
        public boolean isPermanent() {
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(priority);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            final MockFlowRule other = (MockFlowRule) obj;
            return Objects.equals(this.priority, other.priority);
        }

        @Override
        public Type type() {
            return type;
        }
    }

    public static class MockIntent extends Intent {
        private static AtomicLong counter = new AtomicLong(0);

        private final Long number;

        public MockIntent(Long number) {
            super(NetTestTools.APP_ID, Collections.emptyList());
            this.number = number;
        }

        public MockIntent(Long number, Collection<NetworkResource> resources) {
            super(NetTestTools.APP_ID, resources);
            this.number = number;
        }

        public Long number() {
            return number;
        }

        public static Long nextId() {
            return counter.getAndIncrement();
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(getClass())
                    .add("id", id())
                    .add("appId", appId())
                    .toString();
        }
    }

    public static class MockTimestamp implements Timestamp {
        final int value;

        public MockTimestamp(int value) {
            this.value = value;
        }

        @Override
        public int compareTo(Timestamp o) {
            if (!(o instanceof MockTimestamp)) {
                return -1;
            }
            MockTimestamp that = (MockTimestamp) o;
            return (this.value > that.value ? -1 : (this.value == that.value ? 0 : 1));
        }
    }


}

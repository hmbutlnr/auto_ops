package com.autoops.host.repository;

import com.autoops.host.model.Host;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
public class HostRepository {

    private final Map<Long, Host> hostStore = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @PostConstruct
    public void init() {
        Host host = new Host();
        host.setName("172.19.63.51");
        host.setHostname("172.19.63.51");
        host.setPort(22);
        host.setUsername("lifh");
        host.setPassword("19981028");
        host.setStatus("UNKNOWN");
        host.setId(idGenerator.getAndIncrement());
        hostStore.put(host.getId(), host);
    }

    public Host save(Host host) {
        if (host.getId() == null) {
            host.setId(idGenerator.getAndIncrement());
        }
        hostStore.put(host.getId(), host);
        return host;
    }

    public Host findById(Long id) {
        return hostStore.get(id);
    }

    public List<Host> findAll() {
        return new ArrayList<>(hostStore.values());
    }

    public void deleteById(Long id) {
        hostStore.remove(id);
    }

    public boolean existsById(Long id) {
        return hostStore.containsKey(id);
    }

    public List<Host> findByStatus(String status) {
        return hostStore.values().stream()
                .filter(h -> status.equalsIgnoreCase(h.getStatus()))
                .collect(Collectors.toList());
    }

    public long count() {
        return hostStore.size();
    }

    public void clear() {
        hostStore.clear();
        idGenerator.set(1);
    }
}
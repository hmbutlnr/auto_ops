package com.autoops.host.controller;

import com.autoops.host.model.Host;
import com.autoops.host.repository.HostRepository;
import com.autoops.service.SshService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/host")
@CrossOrigin(origins = "*")
public class HostController {

    private final HostRepository hostRepository;
    private final SshService sshService;

    public HostController(HostRepository hostRepository, SshService sshService) {
        this.hostRepository = hostRepository;
        this.sshService = sshService;
    }

    @GetMapping
    public Map<String, Object> getAllHosts() {
        List<Host> hosts = hostRepository.findAll();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", hosts);
        response.put("count", hosts.size());
        return response;
    }

    @GetMapping("/{id}")
    public Map<String, Object> getHostById(@PathVariable Long id) {
        Host host = hostRepository.findById(id);
        Map<String, Object> response = new HashMap<>();
        if (host != null) {
            response.put("success", true);
            response.put("data", host);
        } else {
            response.put("success", false);
            response.put("error", "主机不存在");
        }
        return response;
    }

    @PostMapping
    public Map<String, Object> createHost(@RequestBody Host host) {
        Map<String, Object> response = new HashMap<>();
        try {
            if (host.getPort() == null) {
                host.setPort(22);
            }
            if (host.getStatus() == null) {
                host.setStatus("UNKNOWN");
            }
            Host saved = hostRepository.save(host);
            response.put("success", true);
            response.put("data", saved);
            response.put("message", "主机添加成功");
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        return response;
    }

    @PutMapping("/{id}")
    public Map<String, Object> updateHost(@PathVariable Long id, @RequestBody Host host) {
        Map<String, Object> response = new HashMap<>();
        Host existing = hostRepository.findById(id);
        if (existing == null) {
            response.put("success", false);
            response.put("error", "主机不存在");
            return response;
        }
        try {
            if (host.getName() != null) {
                existing.setName(host.getName());
            }
            if (host.getHostname() != null) {
                existing.setHostname(host.getHostname());
            }
            if (host.getPort() != null) {
                existing.setPort(host.getPort());
            }
            if (host.getUsername() != null) {
                existing.setUsername(host.getUsername());
            }
            if (host.getPassword() != null && !host.getPassword().isEmpty()) {
                existing.setPassword(host.getPassword());
            }
            if (host.getDescription() != null) {
                existing.setDescription(host.getDescription());
            }
            hostRepository.save(existing);
            response.put("success", true);
            response.put("data", existing);
            response.put("message", "主机更新成功");
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        return response;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> deleteHost(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        Host existing = hostRepository.findById(id);
        if (existing == null) {
            response.put("success", false);
            response.put("error", "主机不存在");
            return response;
        }
        hostRepository.deleteById(id);
        response.put("success", true);
        response.put("message", "主机删除成功");
        return response;
    }

    @PostMapping("/{id}/test")
    public Map<String, Object> testConnection(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        Host host = hostRepository.findById(id);
        if (host == null) {
            response.put("success", false);
            response.put("message", "主机不存在");
            return response;
        }
        try {
            String result = sshService.testConnection(host.getHostname(), host.getPort(), host.getUsername(), host.getPassword());
            host.setStatus("ONLINE");
            hostRepository.save(host);
            response.put("success", true);
            response.put("message", "连接成功: " + result);
        } catch (Exception e) {
            host.setStatus("OFFLINE");
            hostRepository.save(host);
            response.put("success", false);
            response.put("message", "连接失败: " + e.getMessage());
        }
        return response;
    }

    @PostMapping("/refresh-status")
    public Map<String, Object> refreshAllStatus() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Host> hosts = hostRepository.findAll();
            for (Host host : hosts) {
                try {
                    sshService.testConnection(host.getHostname(), host.getPort(), host.getUsername(), host.getPassword());
                    host.setStatus("ONLINE");
                } catch (Exception e) {
                    host.setStatus("OFFLINE");
                }
                hostRepository.save(host);
            }
            response.put("success", true);
            response.put("data", hosts);
            response.put("message", "状态刷新完成");
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        return response;
    }
}
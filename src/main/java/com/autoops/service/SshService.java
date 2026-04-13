package com.autoops.service;

import com.autoops.config.SshProperties;
import com.autoops.host.model.Host;
import com.autoops.host.repository.HostRepository;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class SshService {

    private final SshProperties sshProperties;
    private final HostRepository hostRepository;

    public SshService(SshProperties sshProperties, HostRepository hostRepository) {
        this.sshProperties = sshProperties;
        this.hostRepository = hostRepository;
    }

    public String executeCommand(String command) {
        return executeCommandOnHost(null, command);
    }

    public String executeCommandOnHost(Long hostId, String command) {
        Session session = null;
        ChannelExec channel = null;
        
        try {
            String targetHost;
            int targetPort;
            String targetUsername;
            String targetPassword;
            
            if (hostId != null) {
                Host host = hostRepository.findById(hostId);
                if (host == null) {
                    return "错误: 主机不存在 (ID: " + hostId + ")";
                }
                targetHost = host.getHostname();
                targetPort = host.getPort();
                targetUsername = host.getUsername();
                targetPassword = host.getPassword();
                log.info("在指定主机上执行命令: {} ({}:{})", host.getName(), targetHost, targetPort);
            } else {
                targetHost = sshProperties.getHost();
                targetPort = sshProperties.getPort();
                targetUsername = sshProperties.getUsername();
                targetPassword = sshProperties.getPassword();
                log.info("在默认主机上执行命令: {}:{}", targetHost, targetPort);
            }
            
            log.info("执行 SSH 命令: {}", command);
            
            JSch jsch = new JSch();
            session = jsch.getSession(targetUsername, targetHost, targetPort);
            session.setPassword(targetPassword);
            session.setConfig("StrictHostKeyChecking", "no");
            session.setTimeout(sshProperties.getTimeout());
            session.connect();
            log.info("SSH连接成功: {}:{}", targetHost, targetPort);
            
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            
            InputStream outputStream = channel.getInputStream();
            InputStream errorStream = channel.getErrStream();
            channel.connect();
            
            StringBuilder output = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(outputStream, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            
            StringBuilder errorOutput = new StringBuilder();
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(errorStream, StandardCharsets.UTF_8));
            while ((line = errorReader.readLine()) != null) {
                errorOutput.append(line).append("\n");
            }
            
            while (!channel.isClosed()) {
                Thread.sleep(100);
            }
            
            int exitStatus = channel.getExitStatus();
            log.info("命令执行完成，退出码: {}", exitStatus);
            
            StringBuilder result = new StringBuilder();
            if (hostId != null) {
                Host host = hostRepository.findById(hostId);
                result.append("主机: ").append(host != null ? host.getName() : "未知").append("\n");
            }
            result.append("命令: ").append(command).append("\n");
            result.append("退出码: ").append(exitStatus).append("\n\n");
            
            if (exitStatus == 0) {
                result.append("输出:\n");
                result.append(output.toString().trim());
            } else {
                result.append("错误:\n");
                result.append(errorOutput.toString().trim());
            }
            
            return result.toString();
            
        } catch (Exception e) {
            log.error("SSH命令执行失败: {}", e.getMessage(), e);
            throw new RuntimeException("SSH命令执行失败: " + e.getMessage());
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
                log.info("SSH连接已关闭");
            }
        }
    }

    public String testConnection(String hostname, int port, String username, String password) {
        Session session = null;
        try {
            log.info("测试连接: {}:{}", hostname, port);
            
            JSch jsch = new JSch();
            session = jsch.getSession(username, hostname, port);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.setTimeout(sshProperties.getTimeout());
            session.connect();
            
            String result = "SSH连接正常";
            log.info("测试连接成功: {}:{}", hostname, port);
            return result;
            
        } catch (Exception e) {
            log.error("测试连接失败: {}:{} - {}", hostname, port, e.getMessage());
            throw new RuntimeException("连接失败: " + e.getMessage());
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }
    
    public void disconnect() {
        log.info("SSH服务已清理");
    }
}
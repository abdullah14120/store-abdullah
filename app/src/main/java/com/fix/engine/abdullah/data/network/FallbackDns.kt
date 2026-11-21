package com.fix.engine.abdullah.data.network

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Developed by: Abdullah Al-Tamimi
 * Inspired by: Orion Store FallbackDns
 * Feature: System DNS first (filtering 0.0.0.0/loopback sinkholes), then Cloudflare & Google DoH fallback
 */
object FallbackDns : Dns {

    private val dohResolvers: List<Dns> by lazy {
        val bootstrapClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()

        val resolvers = mutableListOf<Dns>()
        runCatching {
            resolvers.add(
                DnsOverHttps.Builder()
                    .client(bootstrapClient)
                    .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
                    .bootstrapDnsHosts(
                        InetAddress.getByName("1.1.1.1"),
                        InetAddress.getByName("1.0.0.1")
                    )
                    .build()
            )
        }
        runCatching {
            resolvers.add(
                DnsOverHttps.Builder()
                    .client(bootstrapClient)
                    .url("https://dns.google/dns-query".toHttpUrl())
                    .bootstrapDnsHosts(
                        InetAddress.getByName("8.8.8.8"),
                        InetAddress.getByName("8.8.4.4")
                    )
                    .build()
            )
        }
        resolvers
    }

    override fun lookup(hostname: String): List<InetAddress> {
        val sys = runCatching { Dns.SYSTEM.lookup(hostname) }
            .getOrNull()
            ?.filterNot { it.isAnyLocalAddress || it.isLoopbackAddress }
        if (!sys.isNullOrEmpty()) return sys

        for (resolver in dohResolvers) {
            val resolved = runCatching { resolver.lookup(hostname) }
                .getOrNull()
                ?.filterNot { it.isAnyLocalAddress || it.isLoopbackAddress }
            if (!resolved.isNullOrEmpty()) return resolved
        }

        throw UnknownHostException("Unable to resolve host: $hostname")
    }
}

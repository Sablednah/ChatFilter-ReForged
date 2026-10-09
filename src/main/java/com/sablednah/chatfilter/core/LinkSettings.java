package com.sablednah.chatfilter.core;

import java.util.List;

/**
 * @param ips            also catch bare IP addresses ({@code 192.168.1.20:25565})
 * @param spelledDots    also catch {@code example dot com}, {@code example(.)com} and friends
 * @param tlds           top-level domains that make {@code word.tld} a link
 * @param allowedDomains domains that may always be posted — and their subdomains
 */
public record LinkSettings(CategoryPolicy policy, boolean ips, boolean spelledDots,
        List<String> tlds, List<String> allowedDomains) {
}

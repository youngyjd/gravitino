/*
 * Copyright 2023 Datastrato.
 * This software is licensed under the Apache License version 2.
 */
package com.datastrato.graviton;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StringIdentifier {

  private static final Logger LOG = LoggerFactory.getLogger(StringIdentifier.class);

  public static final String ID_KEY = "graviton.identifier";

  @VisibleForTesting static final int CURRENT_FORMAT_VERSION = 1;

  @VisibleForTesting static final String CURRENT_FORMAT = "graviton.v%d.uid%d";

  private static final String STRING_COMMENT = "From Graviton, DO NOT EDIT: ";
  private static final String STRING_COMMENT_FORMAT = "%s(%s%s)";

  private static final Pattern CURRENT_FORMAT_REGEX =
      Pattern.compile("graviton\\.v(\\d+)\\.uid(\\d+)");

  private static final Map<Integer, Pair<String, Pattern>> SUPPORTED_FORMAT =
      ImmutableMap.<Integer, Pair<String, Pattern>>builder()
          .put(CURRENT_FORMAT_VERSION, Pair.of(CURRENT_FORMAT, CURRENT_FORMAT_REGEX))
          .build();

  private final long id;

  private StringIdentifier(long id) {
    this.id = id;
  }

  public static StringIdentifier fromId(long id) {
    return new StringIdentifier(id);
  }

  public static StringIdentifier fromString(String idString) {
    Preconditions.checkArgument(
        StringUtils.isNotBlank(idString), "Input id string cannot be null or empty");

    for (Map.Entry<Integer, Pair<String, Pattern>> entry : SUPPORTED_FORMAT.entrySet()) {
      int supportedVersion = entry.getKey();
      Pair<String, Pattern> format = entry.getValue();
      Pattern pattern = format.getRight();

      Matcher m = pattern.matcher(idString);
      if (m.matches()) {
        int version = Integer.parseInt(m.group(1));
        long id = Long.parseLong(m.group(2));

        if (version != supportedVersion) {
          continue;
        }

        return new StringIdentifier(id);
      }
    }

    throw new IllegalArgumentException("Invalid string identifier format: " + idString);
  }

  public long id() {
    return id;
  }

  @Override
  public String toString() {
    return String.format(CURRENT_FORMAT, CURRENT_FORMAT_VERSION, id);
  }

  /** ******Helper methods to set/get StringIdentifier to/from different places******* */
  public static Map<String, String> addToProperties(
      StringIdentifier stringId, Map<String, String> properties) {
    if (properties == null) {
      return ImmutableMap.of(ID_KEY, stringId.toString());
    }

    if (properties.containsKey(ID_KEY)) {
      LOG.warn(
          "Property {}:{} already existed in the properties, this is unexpected, we will "
              + "ignore adding the identifier to the properties",
          ID_KEY,
          properties.get(ID_KEY));
      return properties;
    }

    return ImmutableMap.<String, String>builder()
        .putAll(properties)
        .put(ID_KEY, stringId.toString())
        .build();
  }

  public static StringIdentifier fromProperties(Map<String, String> properties) {
    if (properties == null) {
      return null;
    }

    String idString = properties.get(ID_KEY);
    if (StringUtils.isBlank(idString)) {
      return null;
    }

    return fromString(idString);
  }

  public static String addToComment(StringIdentifier stringId, String comment) {
    if (StringUtils.isBlank(comment)) {
      return String.format(STRING_COMMENT_FORMAT, "", STRING_COMMENT, stringId.toString());
    }

    return String.format(STRING_COMMENT_FORMAT, comment + " ", STRING_COMMENT, stringId.toString());
  }

  public static StringIdentifier fromComment(String comment) {
    if (StringUtils.isBlank(comment)) {
      return null;
    }

    int index = comment.lastIndexOf('(');
    if (index == -1) {
      return null;
    }

    String idString = comment.substring(index + STRING_COMMENT.length() + 1, comment.length() - 1);
    return fromString(idString);
  }
}
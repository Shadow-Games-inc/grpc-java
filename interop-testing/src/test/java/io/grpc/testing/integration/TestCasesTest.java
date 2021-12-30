/*
 * Copyright 2016 The gRPC Authors
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

package io.grpc.testing.integration;

import static io.grpc.testing.integration.TestCases.fromString;
import static org.junit.Assert.assertEquals;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link TestCases}.
 */
@RunWith(JUnit4.class)
public class TestCasesTest {

  @Test(expected = IllegalArgumentException.class)
  public void unknownStringThrowsException() {
    fromString("does_not_exist_1234");
  }

  @Test
  public void testCaseNamesShouldMapToEnums() {
    // names of testcases as defined in the interop spec
    String[] testCases = {

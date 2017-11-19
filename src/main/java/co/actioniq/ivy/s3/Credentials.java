/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package co.actioniq.ivy.s3;

import com.amazonaws.SDKGlobalConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSCredentialsProviderChain;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.auth.SystemPropertiesCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

class Credentials {
  private Credentials() {}

  static String toEnvironmentVariableName(String s) {
    return s.toUpperCase().replace('-', '_').replace('.', '_').replaceAll("[^A-Z0-9_]", "");
  }

  static AWSCredentialsProvider makeCredentialsProvider(String profile, String bucket) {
    Log.print("makeCredentialsProvider profile: " + profile + ", bucket: " + bucket);
    AWSCredentialsProvider[] basicProviders = {
        new BucketSpecificEnvironmentVariableCredentialsProvider(bucket),
        new BucketSpecificSystemPropertiesCredentialsProvider(bucket),
        new EnvironmentVariableCredentialsProvider(),
        new SystemPropertiesCredentialsProvider(),
        new InstanceProfileCredentialsProvider(),
        new ProfileCredentialsProvider(profile)
    };

    AWSCredentialsProviderChain basicProviderChain = new AWSCredentialsProviderChain(basicProviders);

    AWSCredentialsProvider[] roleProviders = {
        new BucketSpecificRoleBasedEnvironmentVariableCredentialsProvider(basicProviderChain, bucket),
        new BucketSpecificRoleBasedSystemPropertiesCredentialsProvider(basicProviderChain, bucket),
        new RoleBasedEnvironmentVariableCredentialsProvider(basicProviderChain),
        new RoleBasedSystemPropertiesCredentialsProvider(basicProviderChain),
    };

    List<AWSCredentialsProvider> providerList = new ArrayList<>();
    providerList.addAll(Arrays.asList(roleProviders));
    providerList.addAll(Arrays.asList(basicProviders));
    AWSCredentialsProvider[] providerArray = providerList.toArray(new AWSCredentialsProvider[providerList.size()]);

    AWSCredentialsProvider providerChain = new AWSCredentialsProviderChain(providerArray);
    Log.print("credentials: " + providerChain.getCredentials());

    return providerChain;
  }
}


abstract class BucketSpecificCredentialsProvider implements AWSCredentialsProvider {
  private final String bucket;

  BucketSpecificCredentialsProvider(String bucket) {
    this.bucket = bucket;
  }

  public AWSCredentials getCredentials() {
    String accessKey = getProp(AccessKeyName() + "." + bucket, bucket + "." + AccessKeyName());
    String secretKey = getProp(SecretKeyName() + "." + bucket, bucket + "." + SecretKeyName());
    return new BasicAWSCredentials(accessKey, secretKey);
  }

  public void refresh() {}

  protected abstract String AccessKeyName();

  protected abstract String SecretKeyName();

  // This should throw an exception if the value is missing
  protected abstract String getProp(String... names);
}


class BucketSpecificEnvironmentVariableCredentialsProvider extends BucketSpecificCredentialsProvider {
  BucketSpecificEnvironmentVariableCredentialsProvider(String bucket) {
    super(bucket);
  }

  protected String AccessKeyName() {
    return SDKGlobalConfiguration.ACCESS_KEY_ENV_VAR;
  }

  protected String SecretKeyName() {
    return SDKGlobalConfiguration.SECRET_KEY_ENV_VAR;
  }

  protected String getProp(String... names) {
    return Arrays.stream(names)
        .map(n -> System.getenv(Credentials.toEnvironmentVariableName(n)))
        .filter(Objects::nonNull)
        .findFirst()
        .get()
        .trim();
  }
}


class BucketSpecificRoleBasedEnvironmentVariableCredentialsProvider extends RoleBasedEnvironmentVariableCredentialsProvider {
  private final String bucket;

  BucketSpecificRoleBasedEnvironmentVariableCredentialsProvider(AWSCredentialsProviderChain providerChain, String bucket) {
    super(providerChain);
    this.bucket = bucket;
  }

  List<String> RoleArnKeyNames() {
    return Arrays.asList(RoleArnKeyName() + "." + bucket, bucket + "." + RoleArnKeyName());
  }
}


class BucketSpecificRoleBasedSystemPropertiesCredentialsProvider extends RoleBasedSystemPropertiesCredentialsProvider {
  private final String bucket;

  BucketSpecificRoleBasedSystemPropertiesCredentialsProvider(AWSCredentialsProviderChain providerChain, String bucket) {
    super(providerChain);
    this.bucket = bucket;
  }

  List<String> RoleArnKeyNames() {
    return Arrays.asList(RoleArnKeyName() + "." + bucket, bucket + "." + RoleArnKeyName());
  }
}


class BucketSpecificSystemPropertiesCredentialsProvider extends BucketSpecificCredentialsProvider {
  BucketSpecificSystemPropertiesCredentialsProvider(String bucket) {
    super(bucket);
  }

  protected String AccessKeyName() {
    return SDKGlobalConfiguration.ACCESS_KEY_SYSTEM_PROPERTY;
  }

  protected String SecretKeyName() {
    return SDKGlobalConfiguration.SECRET_KEY_SYSTEM_PROPERTY;
  }

  protected String getProp(String... names) {
    return Arrays.stream(names).map(System::getProperty).filter(Objects::nonNull).findFirst().get().trim();
  }
}



abstract class RoleBasedCredentialsProvider implements AWSCredentialsProvider {
  private final AWSCredentialsProviderChain providerChain;

  RoleBasedCredentialsProvider(AWSCredentialsProviderChain providerChain) {
    this.providerChain = providerChain;
  }

  abstract List<String> RoleArnKeyNames();

  // This should throw an exception if the value is missing
  abstract String getRoleArn(List<String> keys);

  public AWSCredentials getCredentials() {
    AWSSecurityTokenServiceClient securityTokenService = new AWSSecurityTokenServiceClient(providerChain);

    AssumeRoleRequest roleRequest = new AssumeRoleRequest()
        .withRoleArn(getRoleArn(RoleArnKeyNames()))
        .withRoleSessionName(String.valueOf(System.currentTimeMillis()));

    AssumeRoleResult result = securityTokenService.assumeRole(roleRequest);

    return new BasicSessionCredentials(result.getCredentials().getAccessKeyId(),
        result.getCredentials().getSecretAccessKey(),
        result.getCredentials().getSessionToken());
  }

  public void refresh() {
  }
}


class RoleBasedEnvironmentVariableCredentialsProvider extends RoleBasedCredentialsProvider {
  RoleBasedEnvironmentVariableCredentialsProvider(AWSCredentialsProviderChain providerChain) {
    super(providerChain);
  }

  String RoleArnKeyName() {
    return "AWS_ROLE_ARN";
  }

  List<String> RoleArnKeyNames() {
    return Collections.singletonList(RoleArnKeyName());
  }

  protected String getRoleArn(List<String> keys) {
    return keys.stream()
        .map(k -> System.getenv(Credentials.toEnvironmentVariableName(k)))
        .filter(Objects::nonNull)
        .findFirst()
        .get()
        .trim();
  }
}


class RoleBasedSystemPropertiesCredentialsProvider extends RoleBasedCredentialsProvider {
  RoleBasedSystemPropertiesCredentialsProvider(AWSCredentialsProviderChain providerChain) {
    super(providerChain);
  }

  String RoleArnKeyName() {
    return "aws.roleArn";
  }

  List<String> RoleArnKeyNames() {
    return Collections.singletonList(RoleArnKeyName());
  }

  String getRoleArn(List<String> keys) {
    return keys.stream().map(System::getProperty).filter(Objects::nonNull).findFirst().get().trim();
  }
}

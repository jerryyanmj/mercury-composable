/*

    Copyright 2018-2024 Accenture Technology

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

 */

package com.accenture.tasks;

import com.accenture.support.TestBase;
import org.junit.Assert;
import org.junit.Test;
import org.platformlambda.core.models.EventEnvelope;
import org.platformlambda.core.system.PostOffice;
import org.platformlambda.core.util.Utility;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class CryptoTests extends TestBase {
    private static final Utility util = Utility.getInstance();

    @SuppressWarnings("unchecked")
    @Test
    public void encryptAndDecryptTest() throws IOException, ExecutionException, InterruptedException {
        String b64Key = util.bytesToBase64(crypto.generateAesKey(strongCrypto? 256 : 128));
        PostOffice po = new PostOffice("unit.test", "1000", "TEST /crypto");
        String KEY1 = "k1";
        String KEY2 = "k2";
        String KEY1_DATA = "hello";
        String KEY2_DATA = "world";
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> dataset = new HashMap<>();
        input.put("protected_fields", "k1, k2");
        input.put("b64_key", b64Key);
        dataset.put(KEY1, KEY1_DATA);
        dataset.put(KEY2, KEY2_DATA);
        input.put("dataset", dataset);
        // send event to encryption function
        EventEnvelope encRequest = new EventEnvelope().setTo("v1.encrypt.fields").setBody(input);
        EventEnvelope encResult = po.request(encRequest, 5000).get();
        Assert.assertTrue(encResult.getBody() instanceof Map);
        Map<String, Object> encrypted = (Map<String, Object>) encResult.getBody();
        Assert.assertEquals(2, encrypted.size());
        Assert.assertTrue(encrypted.containsKey(KEY1));
        Assert.assertTrue(encrypted.containsKey(KEY2));
        Assert.assertNotEquals(KEY1_DATA, encrypted.get(KEY1));
        Assert.assertNotEquals(KEY2_DATA, encrypted.get(KEY2));
        // update encrypted dataset
        input.put("dataset", encrypted);
        // send event to decryption function
        EventEnvelope decRequest = new EventEnvelope().setTo("v1.decrypt.fields").setBody(input);
        EventEnvelope decResult = po.request(decRequest, 5000).get();
        Assert.assertTrue(decResult.getBody() instanceof Map);
        Map<String, Object> decrypted = (Map<String, Object>) decResult.getBody();
        Assert.assertEquals(2, decrypted.size());
        Assert.assertTrue(decrypted.containsKey(KEY1));
        Assert.assertTrue(decrypted.containsKey(KEY2));
        Assert.assertEquals(KEY1_DATA, decrypted.get(KEY1));
        Assert.assertEquals(KEY2_DATA, decrypted.get(KEY2));
    }

}

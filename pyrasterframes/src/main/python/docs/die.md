# Badness detector?


```python
a = 2
a
```

```
2
```



assume some declaration of b is made but not able to complete for some wierd reason


```python
from japanese_breakfast import Rice
b = Rice(a)
```

```
---------------------------------------------------------------------------ModuleNotFoundError
Traceback (most recent call last)<ipython-input-1-166af07933d7> in
<module>
----> 1 from japanese_breakfast import Rice
      2 b = Rice(a)
ModuleNotFoundError: No module named 'japanese_breakfast'
```



That will fail silently with probably import error. Next block will fail with NameError


```python
b * 23
```

```
---------------------------------------------------------------------------NameError
Traceback (most recent call last)<ipython-input-1-6fe76f65b0ea> in
<module>
----> 1 b * 23
NameError: name 'b' is not defined
```



Now try to see if things failed

First for one that won't fail




And now for one that will probably fail



@ref:[bad link](nonexistent_page.md)

